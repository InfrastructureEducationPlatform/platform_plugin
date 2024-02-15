package com.example.demo.web

class RegexObj {
    fun verifyWebServerName(newName: String) : Boolean = newName.matches(Regex("^.{1,100}$"))
    fun verifyDbName(newName: String) : Boolean = newName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$"))
    fun verifyDbUserName(newName: String) : Boolean = newName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]{0,15}$"))
    fun verifyDbUserPassword(newName: String) : Boolean = newName.matches(Regex("[^\"/@]{8,128}"))
}