package com.dropbridge.app

import android.content.Context
import android.net.Uri

object Store {
    fun get(c: Context): String? = c.getSharedPreferences("db", 0).getString("url", null)
    fun set(c: Context, u: String) { c.getSharedPreferences("db", 0).edit().putString("url", u).apply() }
    fun base(u: String): String { val x = Uri.parse(u); return "${x.scheme}://${x.authority}" }
    fun token(u: String): String? = Uri.parse(u).getQueryParameter("k")
}
