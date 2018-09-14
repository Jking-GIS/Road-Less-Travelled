package com.example.jeff9123.displaymap;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;

public class NewLocationActivity extends AppCompatActivity {

    public static final String LOCATION_REPLY = "location_sql_reply";
    public static final String LATITUDE_REPLY = "latitude_sql_reply";
    public static final String LONGITUDE_REPLY = "longitude_sql_reply";

    private EditText mEditLocationView;
    private EditText mEditLatitudeView;
    private EditText mEditLongitudeView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_location);
        mEditLocationView = findViewById(R.id.edit_location);
        mEditLatitudeView = findViewById(R.id.edit_latitude);
        mEditLongitudeView = findViewById(R.id.edit_longitude);

        final Button button = findViewById(R.id.button_save);
        button.setOnClickListener(view -> {
            Intent replyIntent = new Intent();
            if (TextUtils.isEmpty(mEditLocationView.getText())) {
                setResult(RESULT_CANCELED, replyIntent);
            } else {
                String location = mEditLocationView.getText().toString();
                double latitude = Double.parseDouble(mEditLatitudeView.getText().toString());
                double longitude = Double.parseDouble(mEditLongitudeView.getText().toString());

                replyIntent.putExtra(LOCATION_REPLY, location);
                replyIntent.putExtra(LATITUDE_REPLY, latitude);
                replyIntent.putExtra(LONGITUDE_REPLY, longitude);
                setResult(RESULT_OK, replyIntent);
            }
            finish();
        });
    }

}
