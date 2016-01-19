package edu.uw.cwc8.sunspotter;

import android.content.Context;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Sunspotter";
    private ArrayAdapter<String> adapter;
    private EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editText = (EditText) findViewById(R.id.txtInput);
        Button button = (Button) findViewById(R.id.button);

        ArrayList<String> list = new ArrayList<>();

        adapter = new ArrayAdapter<String>(
                this, R.layout.list_item, R.id.txtItem, list);

        AdapterView listView = (AdapterView)findViewById(R.id.listView);
        listView.setAdapter(adapter);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String zipCode = editText.getText().toString();
                Log.v(TAG, "Your zip code is " + zipCode);

                //model
                WeatherSearchTask task = new WeatherSearchTask();
                task.execute(zipCode);

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                        InputMethodManager.RESULT_UNCHANGED_SHOWN);
            }
        });

    }


    public class WeatherSearchTask extends AsyncTask<String, Void, JSONObject> {

        //download JSON string from OpenWeatherMap with the given city name
        //return the JSON string as a JSON Object with forecast data
        protected JSONObject doInBackground(String... params){

            String zipCode = params[0].trim().replace(" ", "%20");

            //construct the url for the omdbapi API
            String urlString = "";
            Log.v(TAG, "Constructing URL String");
            urlString = "http://api.openweathermap.org/data/2.5/forecast?q=" + zipCode + "&APPID=09c59918b78220db20de151e6a44939a";
            Log.v(TAG, "URL String is " + urlString);

            HttpURLConnection urlConnection = null;
            BufferedReader bReader = null;

            JSONObject weathers = null;

            try {
                Log.v(TAG, "Openining connection");
                URL url = new URL(urlString);

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }
                bReader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = bReader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    return null;
                }

                String results = buffer.toString();
                Log.v("ListActivity", results);
                try {
                    weathers = new JSONObject(results);
                } catch (JSONException e) {
                    Log.e("Main", "JSON Exception", e);
                }
            }
            catch (IOException e) {
                Log.e("Main", "IO Exception", e);
                return null;
            }

            finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (bReader != null) {
                    try {
                        bReader.close();
                    }
                    catch (final IOException e) {
                    }
                }
            }
            Log.v("Main", weathers.toString());
            return weathers;
        }

        //pull forecast data from the JSON Object and display them in a formatted manner
        protected void onPostExecute(JSONObject weathers){
            //super.onPostExecute(weathers);

            Boolean sunny = false;
            Date sunnyDay = null;
            Log.v(TAG, "Retrieved JSON: " + weathers);

            //check if weathers content legit forecast data
            Boolean hasData = false;
            try {
                String error = weathers.getString("cod");
                if(!error.equals("404")) {
                    hasData = true;
                }
            } catch (JSONException e){
                Log.e("Main", "JSON Exception");
            }

            if(hasData && weathers != null) {
                adapter.clear();

                try {
                    String dayWeather = "";
                    long dt = 0;
                    Date date = null;
                    SimpleDateFormat sdf = new SimpleDateFormat("c K:mma", Locale.US);
                    String dayTime = "";
                    Double dayTemp = 0.0;

                    //retrieve the list from retrieved weathers
                    JSONArray jsonArr = null;
                    try {
                        jsonArr = weathers.getJSONArray("list");
                    } catch (JSONException e) {
                        Log.e("Main", "JSON Exception", e);
                    }

                    for (int i = 0; i < jsonArr.length(); i++) {
                        String result = "";
                        JSONObject jsonObj = jsonArr.getJSONObject(i);

                        //get the weather type
                        dayWeather = jsonObj.getJSONArray("weather").getJSONObject(0).get("main").toString();

                        //convert UNIX time to simple date format
                        dt = jsonObj.getLong("dt");
                        date = new Date(dt * 1000L);
                        dayTime = sdf.format(date);

                        //convert temp from Kelvin to Celcius
                        dayTemp = jsonObj.getJSONObject("main").getDouble("temp") - 273.00;

                        //check earliest sunny time
                        if(dayWeather.equals("Clear")){
                            result += "SUN! (";
                            if(!sunny) {
                                sunny = true;
                                sunnyDay = date;
                            }
                        } else {
                            result += "No sun... (";
                        }
                        result += dayTime +") - ";
                        result += dayTemp.intValue() + "\u00b0";

                        adapter.add(result);
                    }

                    //inflate stub and change content
                    ViewStub stub = (ViewStub) findViewById(R.id.stub);
                    if(stub != null) {
                        Log.v(TAG, "Inflating stub");
                        stub.inflate();
                    }

                    ImageView img = (ImageView) findViewById(R.id.weatherImg);
                    TextView wtitle = (TextView) findViewById(R.id.weatherTitle);
                    TextView wtext = (TextView) findViewById(R.id.weatherText);

                    if(sunny){
                        //sunny day case
                        img.setImageResource(R.drawable.sun);
                        wtitle.setText("There will be Sun!");
                        wtext.setText("It will be sunny on " + sunnyDay);
                    } else {
                        //not-sunny day case
                        img.setImageResource(R.drawable.nosun);
                        wtitle.setText("No sun is found :'(");
                        wtext.setText("Time to stay at home all day _(:з」∠)_");
                    }
                } catch (JSONException exception) {
                    exception.printStackTrace();
                }

            } else {
                //city-not-found case
                adapter.clear();
                //inflate stub and change content
                ViewStub stub = (ViewStub) findViewById(R.id.stub);
                if(stub != null) {
                    Log.v(TAG, "Inflating stub");
                    stub.inflate();
                }

                ImageView img = (ImageView) findViewById(R.id.weatherImg);
                TextView wtitle = (TextView) findViewById(R.id.weatherTitle);
                TextView wtext = (TextView) findViewById(R.id.weatherText);

                img.setImageResource(R.drawable.nosun);
                wtitle.setText("Ah Oh ._.");
                wtext.setText("404 City Not Found");
            }

        }

    }
}
