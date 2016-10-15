/*
 * Copyright (C) 2013 David van Tonder
 * Copyright (C) 2016 Benoit Touchette
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use context file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.draekko.clocklock.weather;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.draekko.clocklock.misc.Constants;
import com.draekko.clocklock.misc.IconUtils;
import com.draekko.clocklock.misc.Preferences;
import com.draekko.clocklock.weather.WeatherInfo.DayForecast;
import com.draekko.clocklock.R;

import static com.draekko.clocklock.misc.WidgetUtils.formattedDate;

public class ForecastBuilder {
    private static final String TAG = "ForecastBuilder";

    /**
     * This method is used to build the full current conditions and horizontal forecasts
     * panels
     *
     * @param context
     * @param w = the Weather info object that contains the forecast data
     * @return = a built view that can be displayed
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static View buildFullPanel(Context context, int resourceId, WeatherInfo w) {

        // Load some basic settings
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
        int color = Preferences.weatherFontColor(context);
        boolean invertLowHigh = Preferences.invertLowHighTemperature(context);
        boolean ampm = Preferences.showAmPmIndicator(context);

        View view = inflater.inflate(resourceId, null);

        // Set the weather source
        TextView weatherSource = (TextView) view.findViewById(R.id.weather_source);
        weatherSource.setText(Preferences.weatherProviderString(context));

        // Set the current conditions
        // Weather Image
        ImageView weatherImage = (ImageView) view.findViewById(R.id.weather_image);
        String iconsSet = Preferences.getWeatherIconSet(context);
        weatherImage.setImageBitmap(w.getConditionBitmap(iconsSet, color,
                IconUtils.getNextHigherDensity(context)));

        // Weather Condition
        TextView weatherCondition = (TextView) view.findViewById(R.id.weather_condition);
        weatherCondition.setText(w.getCondition());

        // Weather Temps
        TextView weatherTemp = (TextView) view.findViewById(R.id.weather_temp);
        weatherTemp.setText(w.getFormattedTemperature());

        // City
        TextView city = (TextView) view.findViewById(R.id.weather_city);
        if (city != null) {
            city.setText(w.getCity());
        }

        // Weather Update Time
        Date lastUpdate = w.getTimestamp();
        StringBuilder sb = new StringBuilder();
        sb.append(DateFormat.format("E", lastUpdate));
        sb.append(" ");
        sb.append(DateFormat.getTimeFormat(context).format(lastUpdate));
        TextView updateTime = (TextView) view.findViewById(R.id.update_time);
        updateTime.setText(w.getCity() + ", " + sb.toString());
        updateTime.setVisibility(Preferences.showWeatherTimestamp(context) ? View.VISIBLE : View.GONE);

        // Weather Temps Panel additional items
        final String low = w.getFormattedLow();
        final String high = w.getFormattedHigh();
        TextView weatherLowHigh = (TextView) view.findViewById(R.id.weather_low_high);
        if (weatherLowHigh != null) {
            weatherLowHigh.setText(invertLowHigh ? high + " | " + low : low + " | " + high);
        }
        TextView weatherLow = (TextView) view.findViewById(R.id.weather_low);
        if (weatherLow != null) {
            weatherLow.setText(invertLowHigh ? low : high);
        }
        TextView weatherHigh = (TextView) view.findViewById(R.id.weather_high);
        if (weatherHigh != null) {
            weatherHigh.setText(invertLowHigh ? high : low);
        }

        TextView weatherWind = (TextView) view.findViewById(R.id.weather_windspeed);
        String direction = w.getWindDirection();
        String speed = w.getFormattedWindSpeed();
        weatherWind.setText(direction + ", " + speed);

        TextView weatherHumidity = (TextView) view.findViewById(R.id.weather_humidity);
        if (weatherHumidity != null) {
            weatherHumidity.setText(w.getFormattedHumidity());
        }

        TextView weatherSunUV = (TextView) view.findViewById(R.id.weather_uv);
        if (weatherSunUV != null) {
            String UV;
            if (w.getSunUV() < 0) {
                UV = "NA";
            } else {
                UV = String.valueOf(w.getSunUV());
            }
            weatherSunUV.setText(UV);
        }

        TextView weatherPressure = (TextView) view.findViewById(R.id.weather_pressure);
        if (weatherPressure != null) {
            weatherPressure.setText(w.getFormattedPressure());
        }

        TextView weatherSunrise = (TextView) view.findViewById(R.id.weather_sunrise);
        if (weatherSunrise != null) {
            String sunrise = formattedDate(context, w.getSunrise(), ampm);
            weatherSunrise.setText(sunrise);
        }

        TextView weatherSunset = (TextView) view.findViewById(R.id.weather_sunset);
        if (weatherSunset != null) {
            String sunset = formattedDate(context, w.getSunset(), ampm);
            weatherSunset.setText(sunset);
        }


        // Get things ready
        LinearLayout forecastView = (LinearLayout) view.findViewById(R.id.forecast_view);
        final View progressIndicator = view.findViewById(R.id.progress_indicator);

        // Build the forecast panel
        if (buildSmallPanel(context, forecastView, w)) {
            // Success, hide the progress container
            progressIndicator.setVisibility(View.GONE);
        }

        return view;
    }

    /**
     * This method is used to build the small, horizontal forecasts panel
     * @param context
     * @param smallPanel = a horizontal linearlayout that will contain the forecasts
     * @param w = the Weather info object that contains the forecast data
     */
    public static boolean buildSmallPanel(Context context, LinearLayout smallPanel, WeatherInfo w) {
      if (smallPanel == null) {
          Log.d(TAG, "Invalid view passed");
          return false;
      }

      // Get things ready
      LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
      int color = Preferences.weatherFontColor(context);
      boolean invertLowHigh = Preferences.invertLowHighTemperature(context);

      ArrayList<DayForecast> forecasts = w.getForecasts();
      if (forecasts == null || forecasts.size() <= 1) {
          smallPanel.setVisibility(View.GONE);
          return false;
      }

      TimeZone MyTimezone = TimeZone.getDefault();
      Calendar calendar = new GregorianCalendar(MyTimezone);

      int numForecasts = forecasts.size();
      int itemSidePadding = context.getResources().getDimensionPixelSize(R.dimen.forecast_item_padding_side);

      // Iterate through the Forecasts
      for (int count = 0; count < numForecasts; count ++) {
          DayForecast d = forecasts.get(count);

          // Load the views
          View forecastItem = inflater.inflate(R.layout.forecast_item, null);

          // The day of the week
          TextView day = (TextView) forecastItem.findViewById(R.id.forecast_day);
          day.setText(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()));
          calendar.roll(Calendar.DAY_OF_WEEK, true);

          // Weather Image
          ImageView image = (ImageView) forecastItem.findViewById(R.id.weather_image);
          String iconsSet = Preferences.getWeatherIconSet(context);
          int resId = d.getConditionResource(context, iconsSet);
          if (resId != 0) {
              image.setImageResource(resId);
          } else {
              image.setImageBitmap(d.getConditionBitmap(context, iconsSet, color));
          }

          // Temperatures
          String dayLow = d.getFormattedLow();
          String dayHigh = d.getFormattedHigh();
          TextView temps = (TextView) forecastItem.findViewById(R.id.weather_temps);
          temps.setText(invertLowHigh ? dayHigh + " " + dayLow : dayLow + " " + dayHigh);

          // Add the view
          smallPanel.addView(forecastItem,
                  new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

          // Add a divider to the right for all but the last view
          if (count < numForecasts - 1) {
                View divider = new View(context);
                smallPanel.addView(divider, new LinearLayout.LayoutParams(
                        itemSidePadding, LinearLayout.LayoutParams.MATCH_PARENT));
          }
      }
      return true;
    }
}
