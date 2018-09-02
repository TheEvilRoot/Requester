package com.theevilroot.requester.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.theevilroot.requester.core.Requester.gson
import com.theevilroot.requester.core.Requester.parser
import khttp.get
import khttp.post
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.*

object Requester {

    lateinit var session: Session

    val parser = JsonParser()
    val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @JvmStatic
    fun main(args: Array<String>) {
        createSession()
    }

    private fun createSession() {
        session = Session()
        val inputStream = System.`in`
        val reader = BufferedReader(InputStreamReader(inputStream))
        while (true) {
            if (session.isPerformed) {
                print("{ + ${session.statusCode} ${session.method} ${session.url} E:${session.isException} } >")
            } else {
                print("${session.method} ${if(session.url != null) session.url.toString() else "NoURL"} P:${session.params.count()} >")
            }
            val command = reader.readLine()
            if (command.isBlank())
                continue
            val args = command.split(" ")
            if (args.count() == 1) {
                if (args[0] == "close") break
                when(args[0]) {
                    "method" -> println(session.method)
                    "url" -> println(session.url)
                    "params" -> {
                        println("Params: ")
                        session.params.entries.forEach {
                            println("\t${it.key} = ${it.value}")
                        }
                    }
                    "headers" -> {
                        println("Headers: ")
                        session.headers.entries.forEach {
                            println("\t${it.key} = ${it.value}")
                        }
                    }
                    "statusCode" -> if (session.isPerformed) {
                        println(session.statusCode)
                    } else {
                        println("Request not performed")
                    }
                    "response"-> if (session.isPerformed) {
                        println(session.response)
                    } else {
                        println("Request not performed")
                    }
                    "exception" -> println(session.exception)
                    "performed" -> println(session.isPerformed)
                    "redir","redirect","allowRedurects" -> println(session.allowRedirects)
                    "timeout" -> println(session.timeout)
                    "perform","start","run","exec" -> if (session.url != null) {
                        println("\nExecuting request ${session.method}:${session.url} with ${session.params.count()} parameters...")
                        session.exec()
                        if (!session.isException)
                            println("\nStatus: ${session.statusCode}\n\n${session.response}")
                        else println("${session.exception!!::class.java}: ${session.exception!!.localizedMessage}")
                    } else {
                        println("URL is not specified. Use set [url=<url>]")
                    }
                    "log" -> {
                        if (session.isPerformed) {
                            println("\n\n")
                            println("Request to ${session.url} with ${session.method} method")
                            println("\nTimeout: ${session.timeout}")
                            println("AllowRedirects: ${session.allowRedirects}")
                            println("\nHeaders: ")
                            if (session.headers.isEmpty())
                                println("\tEmpty")
                            else session.headers.forEach { println("\t${it.key}=${it.value}") }
                            println("Parameters: ")
                            if (session.params.isEmpty())
                                println("\tEmpty")
                            else session.params.forEach { println("\t${it.key}=${it.value}") }
                            println("\nExecuted with status code: ${session.statusCode}")
                            if (session.isException) {
                                println("\nExecuting exception: ${session.exception!!::class.java.name}")
                                println(session.exception!!.localizedMessage)
                            }
                            println("\nResponse: \n${session.response}")
                        } else {
                            println("Perform request before logging it")
                        }
                    }
                    "new" -> {
                        session = Session()
                    }
                }
            } else {
                when(args[0]) {
                    "set" -> if (args.count() >= 2) {
                        val string = args.subList(1, args.count()).joinToString(separator = " ")
                        val entries = "\\[([a-zA-Z]+)=(.+?)\\]".toRegex().findAll(string).toList().map { it.groupValues[1] to it.groupValues[2] }
                        val cls = Session::class.java
                        entries.forEach { entry ->
                            val fields = cls.declaredFields.filter { it.name == entry.first }
                            if(fields.isNotEmpty()) {
                                val field = fields[0]
                                field.isAccessible = true
                                when (field.type) {
                                    String::class.java -> field.set(session, entry.second)
                                    Boolean::class.java -> if (entry.second in arrayOf("true", "false")) {
                                        field.setBoolean(session, entry.second.toBoolean())
                                    } else {
                                        println("Invalid boolean value '${entry.second}'")
                                    }
                                    Double::class.java -> if (entry.second.toDoubleOrNull() != null) {
                                        field.setDouble(session, entry.second.toDouble())
                                    } else {
                                        println("Invalid double value '${entry.second}'")
                                    }
                                    else -> {
                                        println("You can't set this field!")
                                    }
                                }
                            } else {
                                println("Field ${entry.first} not found")
                            }
                        }
                    } else {
                        println("Not enough arguments.\nUse set [name=value] [name=value] ...")
                    }
                    "add" -> if (args.count() >= 3) {
                        val name = args[1]
                        args.subList(2, args.count()).
                                filter { it.split("=", limit=2).count() == 2 }.
                                map { it.split("=", limit=2) }.
                                map { it[0] to it[1] }.forEach {
                            when(name) {
                                "params" -> session.params[it.first] = it.second
                                "headers" -> session.headers[it.first] = it.second
                            }
                        }
                    } else {
                        println("Not enough arguments.\nUse add <param>=<value> ...")
                    }
                }
            }
        }
    }

    private fun showHelpMessage() {
        println("Stub!")
    }
}

class Session {

    fun exec() {
        try {
            val res = if (method == "POST")
                post(url!!, params = params, headers = headers, allowRedirects = allowRedirects, timeout = timeout)
            else get(url!!, params = params, headers = headers, allowRedirects = allowRedirects, timeout = timeout)

            isPerformed = true
            statusCode = res.statusCode
            response = with(isValidJson(res.text)) { if (this == null) res.text else gson.toJson(this) }
        } catch (e: Throwable) {
            isPerformed = true
            isException = true
            exception = e
        }
    }

    var method: String = "GET"
    var url: String? = null
    var params: HashMap<String, String> = HashMap()

    var statusCode: Int = -1
    var response: String? = null

    var allowRedirects: Boolean = false
    var headers: HashMap<String, String> = HashMap()

    var timeout: Double = 8000.0

    var exception: Throwable? = null
    var isException: Boolean = false

    var isPerformed: Boolean = false
}

fun isValidURL(string: String?) = string != null && try { URL(string) ; true } catch (e: Exception) { false }
fun isValidJson(string: String?): JsonElement? {
    return try {
        if (string == null)
            null
        else parser.parse(string)
    } catch (e: Exception) {
        null
    }
}