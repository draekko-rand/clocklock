/*
 * Copyright (C) 2012 The AOKP Project
 * Copyright (C) 2016 Benoit Touchette
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;

import com.draekko.clocklock.R;
import com.draekko.clocklock.misc.IconUtils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

public class WeatherInfo {
    private static final DecimalFormat sNoDigitsFormat = new DecimalFormat("0");

    private Context mContext;

    private String id;
    private String city;
    private String state;
    private String country;
    private long sunrise;
    private long sunset;
    private int sun_uv;
    private String condition;
    private int conditionCode;
    private float temperature;
    private String tempUnit;
    private float humidity;
    private float pressure;
    private float wind;
    private int windDirection;
    private String speedUnit;
    private long timestamp;
    private ArrayList<DayForecast> forecasts;

    public WeatherInfo(Context context, String id, String city, String state, String country, String condition,
                       int conditionCode, float temp, String tempUnit, float humidity,
                       float pressure, float wind, int windDir, String speedUnit,
                       long sunrise, long sunset, int sun_uv,
                       ArrayList<DayForecast> forecasts, long timestamp) {
        this.mContext = context.getApplicationContext();
        this.id = id;
        this.city = city;
        this.state = state;
        this.country = country;
        this.condition = condition;
        this.conditionCode = conditionCode;
        this.humidity = humidity;
        this.pressure = pressure;
        this.wind = wind;
        this.windDirection = windDir;
        this.speedUnit = speedUnit;
        this.timestamp = timestamp;
        this.temperature = temp;
        this.tempUnit = tempUnit;
        this.forecasts = forecasts;
        this.sunrise = sunrise;
        this.sunset = sunset;
        this.sun_uv = sun_uv;
    }

    public static class DayForecast {
        public final float low, high;
        public final int conditionCode;
        public final String condition;

        public DayForecast(float low, float high, String condition, int conditionCode) {
            this.low = low;
            this.high = high;
            this.condition = condition;
            this.conditionCode = conditionCode;
        }

        public String getFormattedLow() {
            return getFormattedValue(low, "\u00b0");
        }

        public String getFormattedHigh() {
            return getFormattedValue(high, "\u00b0");
        }

        public int getConditionResource(Context context, String set) {
            return IconUtils.getWeatherIconResource(context, set, conditionCode);
        }

        public Bitmap getConditionBitmap(Context context, String set, int color) {
            return IconUtils.getWeatherIconBitmap(context, set, color, conditionCode);
        }

        public Bitmap getConditionBitmap(Context context, String set, int color, int density) {
            return IconUtils.getWeatherIconBitmap(context, set, color, conditionCode, density);
        }

        public String getCondition(Context context) {
            return WeatherInfo.getCondition(context, conditionCode, condition);
        }

        public int getConditionCode() {
            return conditionCode;
        }
    }

    public int getConditionResource(String set) {
        return IconUtils.getWeatherIconResource(mContext, set, conditionCode);
    }

    public Bitmap getConditionBitmap(String set, int color) {
        return IconUtils.getWeatherIconBitmap(mContext, set, color, conditionCode);
    }

    public Bitmap getConditionBitmap(String set, int color, int density) {
        return IconUtils.getWeatherIconBitmap(mContext, set, color, conditionCode, density);
    }

    public String getId() {
        return id;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getCountry() {
        return country;
    }

    public int getSunUV() {
        return sun_uv;
    }

    public long getSunrise() {
        return sunrise;
    }

    public long getSunset() {
        return sunset;
    }

    public String getFormatedSunrise() {
        return formatedDate(sunrise, false);
    }

    public String getFormatedSunset() {
        return formatedDate(sunset, false);
    }


    private static String formatedDate(long milliseconds, boolean ampm)  {
        SimpleDateFormat sdf;
        Date currentDate = new Date(milliseconds * 1000);

        if (ampm) {
            sdf = new SimpleDateFormat("hh:mm aa");
        } else {
            sdf = new SimpleDateFormat("HH:mm");
        }

        TimeZone tz = TimeZone.getDefault();
        sdf.setTimeZone(tz);
        return sdf.format(currentDate);
    }

    public String getCondition() {
        return getCondition(mContext, conditionCode, condition);
    }

    public int getConditionCode() {
        return conditionCode;
    }

    private static String getCondition(Context context, int conditionCode, String condition) {
        final Resources res = context.getResources();
        final int resId = res.getIdentifier("weather_" + conditionCode, "string", context.getPackageName());
        if (resId != 0) {
            return res.getString(resId);
        }
        return condition;
    }

    public Date getTimestamp() {
        return new Date(timestamp);
    }

    private static String getFormattedValue(float value, String unit) {
        if (Float.isNaN(value)) {
            return "-";
        }
        String formatted = sNoDigitsFormat.format(value);
        if (formatted.equals("-0")) {
            formatted = "0";
        }
        return formatted + unit;
    }

    public String getFormattedTemperature() {
        return getFormattedValue(temperature, "\u00b0" + tempUnit);
    }

    public String getFormattedLow() {
        return forecasts.get(0).getFormattedLow();
    }

    public String getFormattedHigh() {
        return forecasts.get(0).getFormattedHigh();
    }

    public String getFormattedHumidity() {
        return getFormattedValue(humidity, "%");
    }

    public String getFormattedPressure() {
        return getFormattedValue(pressure, mContext.getString(R.string.weather_hpa));
    }

    public String getFormattedWindSpeed() {
        if (wind < 0) {
            return mContext.getString(R.string.unknown);
        }
        return getFormattedValue(wind, speedUnit);
    }

    public String getWindDirection() {
        int resId;

        if (windDirection < 0) resId = R.string.unknown;
        else if (windDirection < 23) resId = R.string.weather_N;
        else if (windDirection < 68) resId = R.string.weather_NE;
        else if (windDirection < 113) resId = R.string.weather_E;
        else if (windDirection < 158) resId = R.string.weather_SE;
        else if (windDirection < 203) resId = R.string.weather_S;
        else if (windDirection < 248) resId = R.string.weather_SW;
        else if (windDirection < 293) resId = R.string.weather_W;
        else if (windDirection < 338) resId = R.string.weather_NW;
        else resId = R.string.weather_N;

        return mContext.getString(resId);
    }

    public ArrayList<DayForecast> getForecasts() {
        return forecasts;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("WeatherInfo for ");
        builder.append(city + ",");
        builder.append(state + ",");
        builder.append(country);
        builder.append(" (");
        builder.append(id);
        builder.append(") @ ");
        builder.append(getTimestamp());
        builder.append(": ");
        builder.append(getCondition());
        builder.append("(");
        builder.append(conditionCode);
        builder.append("), temperature ");
        builder.append(getFormattedTemperature());
        builder.append(", low ");
        builder.append(getFormattedLow());
        builder.append(", high ");
        builder.append(getFormattedHigh());
        builder.append(", humidity ");
        builder.append(getFormattedHumidity());
        builder.append(", pressure ");
        builder.append(getFormattedPressure());
        builder.append(", wind ");
        builder.append(getFormattedWindSpeed());
        builder.append(" at ");
        builder.append(getWindDirection());
        builder.append(", sunrise ");
        builder.append(getFormatedSunrise());
        builder.append(", sunset ");
        builder.append(getFormatedSunset());
        builder.append(", uv index ");
        builder.append(getSunUV());
        builder.append(", timestamp ");
        builder.append(getTimestamp());
        if (forecasts.size() > 0) {
            builder.append(", forecasts:");
        }
        for (int i = 0; i < forecasts.size(); i++) {
            DayForecast d = forecasts.get(i);
            if (i != 0) {
                builder.append(";");
            }
            builder.append(" day ").append(i + 1).append(": ");
            builder.append("high ").append(d.getFormattedHigh());
            builder.append(", low ").append(d.getFormattedLow());
            builder.append(", ").append(d.condition);
            builder.append("(").append(d.conditionCode).append(")");
        }
        return builder.toString();
    }

    public String toSerializedString() {
        StringBuilder builder = new StringBuilder();
        builder.append(id).append('|');
        builder.append(city).append('|');
        builder.append(state).append('|');
        builder.append(country).append('|');
        builder.append(condition).append('|');
        builder.append(conditionCode).append('|');
        builder.append(temperature).append('|');
        builder.append(tempUnit).append('|');
        builder.append(humidity).append('|');
        builder.append(pressure).append('|');
        builder.append(wind).append('|');
        builder.append(windDirection).append('|');
        builder.append(speedUnit).append('|');
        builder.append(sunrise).append('|');
        builder.append(sunset).append('|');
        builder.append(sun_uv).append('|');
        builder.append(timestamp).append('|');
        serializeForecasts(builder);
        return builder.toString();
    }

    private void serializeForecasts(StringBuilder builder) {
        builder.append(forecasts.size());
        for (DayForecast d : forecasts) {
            builder.append(';');
            builder.append(d.high).append(';');
            builder.append(d.low).append(';');
            builder.append(d.condition).append(';');
            builder.append(d.conditionCode);
        }
    }

    public static WeatherInfo fromSerializedString(Context context, String input) {
        if (input == null) {
            return null;
        }

        String[] parts = input.split("\\|");
        if (parts == null || parts.length != 18) {
            return null;
        }

        long sunrise;
        long sunset;
        int sun_uv;
        int conditionCode, windDirection;
        long timestamp;
        float temperature, humidity, pressure, wind;
        String[] forecastParts = parts[17].split(";");
        int forecastItems;
        ArrayList<DayForecast> forecasts = new ArrayList<DayForecast>();

        // Parse the core data
        try {
            conditionCode = Integer.parseInt(parts[5]);
            temperature = Float.parseFloat(parts[6]);
            humidity = Float.parseFloat(parts[8]);
            pressure = Float.parseFloat(parts[9]);
            wind = Float.parseFloat(parts[10]);
            windDirection = Integer.parseInt(parts[11]);
            sunrise = Long.parseLong(parts[13]);
            sunset = Long.parseLong(parts[14]);
            sun_uv = Integer.parseInt(parts[15]);
            timestamp = Long.parseLong(parts[16]);
            forecastItems = forecastParts == null ? 0 : Integer.parseInt(forecastParts[0]);
        } catch (NumberFormatException e) {
            return null;
        }

        if (forecastItems == 0 || forecastParts.length != 4 * forecastItems + 1) {
            return null;
        }

        // Parse the forecast data
        try {
            for (int item = 0; item < forecastItems; item ++) {
                int offset = item * 4 + 1;
                DayForecast day = new DayForecast(
                        /* low */ Float.parseFloat(forecastParts[offset + 1]),
                        /* high */ Float.parseFloat(forecastParts[offset]),
                        /* condition */ forecastParts[offset + 2],
                        /* conditionCode */ Integer.parseInt(forecastParts[offset + 3]));
                if (!Float.isNaN(day.low) && !Float.isNaN(day.high)) {
                    forecasts.add(day);
                }
            }
        } catch (NumberFormatException ignored) {
        }

        if (forecasts.isEmpty()) {
            return null;
        }

        return new WeatherInfo(context,
                /* id */ parts[0], /* city */ parts[1], /* state */ parts[2], /* country */ parts[3],
                /* condition */ parts[4],
                conditionCode, temperature, /* tempUnit */ parts[7],
                humidity, pressure, wind, windDirection, /* speedUnit */ parts[12],
                sunrise, sunset, sun_uv,
                /* forecasts */ forecasts, timestamp);
    }
}
