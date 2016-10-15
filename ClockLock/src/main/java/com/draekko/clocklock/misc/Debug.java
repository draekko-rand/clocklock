/*
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


package com.draekko.clocklock.misc;


import android.content.Context;

// ===========================================================
// ==[ CLASS ]================================================
// ===========================================================

public class Debug {

    public static boolean doDebug(Context context) {
        boolean debug = Preferences.doDebug(context);
        return debug;
    }

    public static void setDoDebug(Context context, boolean debug) {
        Preferences.setDoDebug(context, debug);
    }

}
