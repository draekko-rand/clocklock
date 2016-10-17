package com.draekko.clocklock.preference;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.draekko.clocklock.ClockWidgetProvider;
import com.draekko.clocklock.R;
import com.draekko.clocklock.misc.Constants;
import com.draekko.clocklock.misc.Preferences;
import com.draekko.clocklock.misc.WidgetUtils;
import com.draekko.clocklock.weather.OpenWeatherMapProvider;
import com.draekko.clocklock.weather.WeatherInfo;
import com.draekko.clocklock.weather.WeatherProvider;
import com.draekko.clocklock.weather.WeatherUndergroundProvider;
import com.draekko.clocklock.weather.WeatherUpdateService;

import static com.draekko.clocklock.misc.Preferences.getPrefs;
import static com.draekko.clocklock.misc.Preferences.setCachedWeatherInfo;
import static com.draekko.clocklock.misc.Preferences.weatherProvider;
import static com.draekko.clocklock.weather.WeatherUpdateService.ACTION_FORCE_UPDATE;

public class RefreshWeather extends Activity {

    private static Context staticContext;
    private static Activity staticActivity;
    private static WeatherProvider weatherProvider;
    private static WeatherInfo weatherInfo;
    private static ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        staticContext = this;
        staticActivity = this;

        setTheme(R.style.transparent);
        setContentView(R.layout.refresh_weather);

        RefreshWeatherAsync refreshWeatherAsync = new RefreshWeatherAsync();
        refreshWeatherAsync.execute();
    }

    private static class RefreshWeatherAsync extends AsyncTask<Void, Void, Void> {

        LocationManager lm = null;
        Location location = null;

        @Override
        protected void onPreExecute() {
            Log.i("AT", "Refreshing weather data");
            Toast.makeText(staticContext, "Refreshing weather data", Toast.LENGTH_LONG);

            progressDialog = new ProgressDialog(staticActivity, android.R.style.Theme_Material_Light_Dialog);
            progressDialog.setMessage("Refreshing weather data");
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setProgress(0);
            progressDialog.show();
        }

        @Override
        protected Void doInBackground(Void[] params) {
            WeatherProvider.LocationResult locationResult = null;
            WeatherInfo weatherInfo = null;
            WeatherProvider weatherProvider;
            String customLocationId = null;
            boolean metric;

            metric = Preferences.useMetricUnits(staticActivity);
            weatherProvider = weatherProvider(staticActivity);

            if (Preferences.useCustomWeatherLocation(staticActivity)) {
                locationResult = new WeatherProvider.LocationResult();
                locationResult.id = Preferences.customWeatherLocationId(staticActivity);
                locationResult.city = Preferences.customWeatherLocationCity(staticActivity);
                locationResult.state = Preferences.customWeatherLocationState(staticActivity);
                locationResult.country = Preferences.customWeatherLocationCountry(staticActivity);
                locationResult.countryName = Preferences.customWeatherLocationCountryName(staticActivity);
                customLocationId = locationResult.id;
                if (weatherProvider != null && customLocationId != null && locationResult != null) {
                    weatherInfo = weatherProvider.getWeatherInfo(customLocationId, locationResult, metric);
                }
            } else {
                location = WidgetUtils.getCurrentLocation(staticActivity, lm, location);
                if (location != null) {
                    weatherInfo = weatherProvider.getWeatherInfo(location, metric);
                }
            }

            if (weatherInfo != null) {
                setCachedWeatherInfo(staticActivity, System.currentTimeMillis(), weatherInfo);

                Intent i = new Intent(staticActivity, WeatherUpdateService.class);
                i.setAction(ACTION_FORCE_UPDATE);
                PendingIntent.getService(staticActivity, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            progressDialog.dismiss();
            staticActivity.finish();
        }
    }
}
