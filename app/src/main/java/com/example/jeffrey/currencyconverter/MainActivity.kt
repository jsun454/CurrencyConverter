package com.example.jeffrey.currencyconverter

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URL
import java.util.concurrent.Executors

const val BASE_URL = "http://apilayer.net/api/"
const val LIVE_RATES_ENDPOINT = "live"
const val CURRENCY_LIST_ENDPOINT = "list" // maybe won't need this if the link w/ rates already has all the currencies
const val ACCESS_KEY = "00898df57dadc0ab15e93afe5cc9ff10"
const val MAX_NUM_CURRENCIES = 10
const val DEFAULT_CURRENCY_A = "USD"
const val DEFAULT_CURRENCY_B = "EUR"
const val DEFAULT_VALUE = 1

class MainActivity : AppCompatActivity() {

    private lateinit var mainGrid: LinearLayout
    private lateinit var standardCurrency: String
    private var userCurrencyList = Array(MAX_NUM_CURRENCIES) {""}
    private var userCurrencyValueList = Array<BigDecimal>(MAX_NUM_CURRENCIES) {BigDecimal.valueOf(0)}
    private val currencyRates = HashMap<String, Object>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainGrid = findViewById(R.id.mainGrid)
        setSingleEvent(mainGrid)

        getCurrencyRates()
        getSavedCurrencyList()

        updateCurrencyList()
        updateCurrencyValues()
        updateDisplay()
    }

    private fun setSingleEvent(mainGrid: LinearLayout) {
        for(i in 0 until mainGrid.childCount) {
            val linearLayout: LinearLayout = mainGrid.getChildAt(i) as LinearLayout
            linearLayout.setOnClickListener {
                Toast.makeText(this@MainActivity, "Click!", Toast.LENGTH_SHORT).show() /*test*/
            }
        }
    }

    private fun getCurrencyRates() {
        // TODO: maybe change this so it only updates if it's been a certain amount of time since the last update (like 1 day)
        try {
            Executors.newSingleThreadExecutor().execute {
                standardCurrency = JSONObject(URL("$BASE_URL$LIVE_RATES_ENDPOINT?access_key=$ACCESS_KEY").readText())["source"].toString()
                val jsonRates = JSONObject(JSONObject(URL("$BASE_URL$LIVE_RATES_ENDPOINT?access_key=$ACCESS_KEY").readText())["quotes"].toString())
                for(key in jsonRates.keys()) {
                    currencyRates[key] = jsonRates.get(key) as Object
                }
                /*for((key, value) in currencyRates) {
                    // TODO: save rates to file to use in case there's no wifi/data or getting live rates doesn't work for some reason
                }*/
            }
        } catch (e: Exception) { // TODO: see what happens when you run the app with no wifi/data
            Log.d("Jeffrey Sun", "Error: " + e.toString())
        }
    }

    private fun getSavedCurrencyList() {
        // TODO: make this check if there is a file that contains a saved currency list first, before defaulting to USD and EUR
        userCurrencyList[0] = DEFAULT_CURRENCY_A
        userCurrencyList[1] = DEFAULT_CURRENCY_B
        userCurrencyValueList[0] = BigDecimal(DEFAULT_VALUE)
    }

    private fun updateCurrencyList() {
        Log.d("Jeffrey Sun", "1")
        // TODO: if a currency was removed ("") shift everything in the array over one and move the ("") to the end
        // TODO: probably need to shift values array too?? should be a better way to do this
        // TODO: read the pseudo code, change if needed
        for(i in 0 until userCurrencyList.size) {
            Log.d("Jeffrey Sun", "Just a filler message")
        }
    }

    private fun updateCurrencyValues() {
        Log.d("Jeffrey Sun", "2")
    }

    private fun updateDisplay() {
        Log.d("Jeffrey Sun", "3")
        for(i in 1..MAX_NUM_CURRENCIES) {
            val currencyID = resources.getIdentifier("currency$i", "id", packageName)
            var currencyEntry: TextView = findViewById(currencyID)
            currencyEntry.text = userCurrencyList[i - 1]

            val valueID = resources.getIdentifier("value$i", "id", packageName)
            var valueEntry: EditText = findViewById(valueID)
            valueEntry.setText(userCurrencyValueList[i - 1].toString())
        }
    }
}