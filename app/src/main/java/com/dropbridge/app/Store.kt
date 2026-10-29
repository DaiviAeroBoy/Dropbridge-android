package com.dropbridge.app

import android.content.Context
import android.net.Uri

object Store {
    private fun p(c: Context) = c.getSharedPreferences("db", 0)
    fun get(c: Context): String? = p(c).getString("url", null)
    fun set(c: Context, u: String) { p(c).edit().putString("url", u).remove("bin").apply() }
    fun bin(c: Context): String? = p(c).getString("bin", null)
    fun setBin(c: Context, b: String) { p(c).edit().putString("bin", b).remove("url").apply() }
    fun base(u: String): String { val x = Uri.parse(u); return "${x.scheme}://${x.authority}" }
    fun token(u: String): String? = Uri.parse(u).getQueryParameter("k")
}
