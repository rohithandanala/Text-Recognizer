package com.rock.textrecognizer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    Button capture, pick;
    ImageView imageview;
    TextView txtview;
    String PicturePath;
    int rotation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        capture = (Button)findViewById(R.id.Capture);
        imageview = (ImageView)findViewById(R.id.imageView);
        txtview = (TextView)findViewById(R.id.textviewer);
        pick = (Button)findViewById(R.id.pick);

        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                if (intent.resolveActivity(getPackageManager())!= null){
                    File file = null;
                    try{
                        file = createImageFile();
                    } catch (IOException ex){
                        showtext("unable to process image.");
                    }
                    if(file != null){
                        Uri imgUri = FileProvider.getUriForFile(getApplicationContext(), "${applicationId}", file);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, imgUri);

                        startActivityForResult(intent, 0);
                    }
                }

            }
        });

        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");

                startActivityForResult(Intent.createChooser(i, "pick an image"), 1);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 0 && resultCode == RESULT_OK && data != null){

            File imgfile = new File(PicturePath);
            Bitmap image = null;
            try {
                image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.fromFile(imgfile));
            } catch (IOException e) {
                e.printStackTrace();
            }

            imageview.setImageBitmap(image);
            FirebaseVisionImage img = FirebaseVisionImage.fromBitmap(image);
            detector(img);

        }
        else if(requestCode == 1 && resultCode == RESULT_OK && data != null){

            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
                Uri ImageU = data.getData();
                Uri_image(ImageU);


            }else if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
                //requesting permission.
            }


        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case 0:{
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    showtext("permission granted.");
                }
            }
        }
    }

    //File object to save in storage.
    private File createImageFile() throws IOException{
            String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imgName = "JPEG_" + time + "_";
            File stdir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File img = File.createTempFile(imgName,".jpg",stdir);
            PicturePath = img.getAbsolutePath();
            return img;
    }

   //displays the toast massage//
    private void showtext(String msg){
        Toast toast = Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_SHORT);
        toast.show();
    }


    private void Uri_image(Uri ImageUri){
        Bitmap image = null;
        try {
            image = BitmapFactory.decodeStream(getContentResolver().openInputStream(ImageUri));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        imageview.setImageBitmap(image);
        FirebaseVisionImage img = FirebaseVisionImage.fromBitmap(image);
        detector(img);
    }

    ///****************to be used*******************///
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    /**
     * Get the angle by which an image must be rotated given the device's current
     * orientation.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int getRotationCompensation(String cameraId, Activity activity, Context context)
            throws CameraAccessException {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = ORIENTATIONS.get(deviceRotation);
        // On most devices, the sensor orientation is 90 degrees, but for some
        // devices it is 270 degrees. For devices with a sensor orientation of
        // 270, rotate the image an additional 180 ((270 + 270) % 360) degrees.
        CameraManager cameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        int sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
        rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360;
        // Return the corresponding FirebaseVisionImageMetadata rotation value.
        int result;
        switch (rotationCompensation) {
            case 0:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                break;
            case 90:
                result = FirebaseVisionImageMetadata.ROTATION_90;
                break;
            case 180:
                result = FirebaseVisionImageMetadata.ROTATION_180;
                break;
            case 270:
                result = FirebaseVisionImageMetadata.ROTATION_270;
                break;
            default:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                Log.e("rotation", "Bad rotation value: " + rotationCompensation);
        }
        return result;
    }


    private void detector(FirebaseVisionImage im){
        FirebaseVisionTextRecognizer textRecognizer = FirebaseVision.getInstance()
                .getOnDeviceTextRecognizer();
        textRecognizer.processImage(im)
                .addOnSuccessListener(
                        new OnSuccessListener<FirebaseVisionText>() {
                            @Override
                            public void onSuccess(FirebaseVisionText firebaseVisionText) {
                                txtview.setText("");
                                List<FirebaseVisionText.TextBlock> blocks = firebaseVisionText.getTextBlocks();
                                if(blocks.size() == 0){
                                    // No text found.
                                    showtext("No text found.");
                                    return;
                                }
                                for(FirebaseVisionText.TextBlock block: firebaseVisionText.getTextBlocks()){
                                    for(FirebaseVisionText.Line line: block.getLines()){
                                        String li = line.getText();
                                        txtview.append(li + "\n" );
                                    }
                                }

                            }
                        }
                ).addOnFailureListener(
                    new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            showtext("Unable to process.");
                        }
                }
        );

    }
}
