package com.ensaj.lab3_ouard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.model.Model;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView resultText;
    private final int imageSize = 224;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        resultText = findViewById(R.id.result);
        Button btnCamera = findViewById(R.id.buttonCamera);
        Button btnGallery = findViewById(R.id.buttonGallery);

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getExtras() != null) {
                        Bitmap bitmap = (Bitmap) result.getData().getExtras().get("data");
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap);
                            classifyImage(bitmap);
                        }
                    }
                }
        );

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                            imageView.setImageBitmap(bitmap);
                            classifyImage(bitmap);
                        } catch (IOException e) {
                            Toast.makeText(this, "Erreur lors du chargement de l'image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraLauncher.launch(intent);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
            }
        });

        btnGallery.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });
    }

    private void classifyImage(Bitmap bitmap) {
        try {
            // Initialiser le modèle
            Model model = Model.createModel(this, "model_unquant.tflite");

            // Préparer l'image
            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(bitmap);

            // Redimensionner ET Normaliser (Crucial pour les modèles Teachable Machine)
            ImageProcessor imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(imageSize, imageSize, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(0.0f, 255.0f)) // Convertit les pixels [0, 255] vers [0.0, 1.0]
                    .build();
            tensorImage = imageProcessor.process(tensorImage);

            // Créer le buffer de sortie (1 ligne, 3 classes)
            TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, 3}, DataType.FLOAT32);

            // Exécuter l'inférence
            Object[] inputs = {tensorImage.getBuffer()};
            Map<Integer, Object> outputs = Map.of(0, outputBuffer.getBuffer());
            model.run(inputs, outputs);

            // Charger les labels
            List<String> labels = FileUtil.loadLabels(this, "labels.txt");

            // Mapper les résultats aux labels
            TensorLabel tensorLabel = new TensorLabel(labels, outputBuffer);
            Map<String, Float> floatMap = tensorLabel.getMapWithFloatValue();

            // Trouver la meilleure prédiction
            String bestLabel = "";
            float maxScore = -1;
            for (Map.Entry<String, Float> entry : floatMap.entrySet()) {
                if (entry.getValue() > maxScore) {
                    maxScore = entry.getValue();
                    bestLabel = entry.getKey();
                }
            }

            // Formater le résultat proprement
            resultText.setText(String.format(Locale.getDefault(), "%s : %.1f%%", bestLabel, maxScore * 100));

            model.close();
        } catch (IOException e) {
            Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraLauncher.launch(intent);
            } else {
                Toast.makeText(this, "Permission caméra refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
