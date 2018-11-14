package com.example.jeffrey.currencyconverter

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URL
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

const val BASE_URL = "http://apilayer.net/api/"
const val LIVE_RATES_ENDPOINT = "live"
const val CURRENCY_LIST_ENDPOINT = "list" // maybe won't need this if the link w/ rates already has all the currencies
const val ACCESS_KEY = "00898df57dadc0ab15e93afe5cc9ff10"
const val MAX_NUM_CURRENCIES = 10
const val ADD_CURRENCY_TEXT = "Add(+)"
const val DEFAULT_CURRENCY_A = "USD"
const val DEFAULT_CURRENCY_B = "EUR"
const val DEFAULT_VALUE = 1

class MainActivity : AppCompatActivity() {

    private lateinit var mainGrid: LinearLayout
    private lateinit var standardCurrency: String
    private var userCurrencyList = Array(MAX_NUM_CURRENCIES) {""}
    private var userCurrencyValueList = Array<BigDecimal>(MAX_NUM_CURRENCIES) {BigDecimal.valueOf(0)}
    private val currencyRates = HashMap<String, Any>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainGrid = findViewById(R.id.mainGrid)
        setSingleEvent(mainGrid)

        val initES: ExecutorService = Executors.newSingleThreadExecutor()
        initES.execute {
            getCurrencyRates()
            getSavedCurrencyList()
            setInputSettings()
            updateCurrencyList()
            updateCurrencyValues(0)
            this@MainActivity.runOnUiThread {
                updateDisplay()
            }
        }
        initES.shutdown()
    }

    private fun setSingleEvent(mainGrid: LinearLayout) {
        for(i in 0 until mainGrid.childCount) {
            val linearLayout: LinearLayout = mainGrid.getChildAt(i) as LinearLayout
            linearLayout.setOnClickListener {// TODO: write a long click listener which pops up a menu {change currency | delete}
                Toast.makeText(this@MainActivity, "Click!", Toast.LENGTH_SHORT).show() /*test*/
            }
        }
    }

    private fun getCurrencyRates() {
        // TODO: maybe change this so it only updates if it's been a certain amount of time since the last update (like 1 day)
        try {
            standardCurrency = JSONObject(URL("$BASE_URL$LIVE_RATES_ENDPOINT?access_key=$ACCESS_KEY").readText())["source"].toString()
            val jsonRates = JSONObject(JSONObject(URL("$BASE_URL$LIVE_RATES_ENDPOINT?access_key=$ACCESS_KEY").readText())["quotes"].toString())
            for(key in jsonRates.keys()) {
                currencyRates[key] = jsonRates.get(key) as Any
            }
            /*for((key, value) in currencyRates) {
                // TODO: save rates to file to use in case there's no wifi/data or getting live rates doesn't work for some reason
            }*/
        } catch (e: Exception) { // TODO: handle the error that happens when you run the app with no wifi/data
            Log.d("Jeffrey Sun", "Error: " + e.toString())
        }
    }

    private fun getSavedCurrencyList() {
        // TODO: make this check if there is a file that contains a saved currency list first, before defaulting to USD and EUR
        userCurrencyList[0] = DEFAULT_CURRENCY_A
        userCurrencyList[1] = DEFAULT_CURRENCY_B
        userCurrencyValueList[0] = BigDecimal(DEFAULT_VALUE)
    }

    private fun setInputSettings() {
        for(i in 0 until MAX_NUM_CURRENCIES) {
            val valueID = resources.getIdentifier("value$i", "id", packageName)
            val valueEntry: EditText = findViewById(valueID)
            valueEntry.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL // TODO: make sure this works
            valueEntry.addTextChangedListener(object : TextWatcher {
                var ignoreChange: Boolean = false
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    if(ignoreChange) {
                        return
                    }
                    ignoreChange = true
                    when {
                        s.toString().replace(".", "").replace("0", "").isEmpty() -> userCurrencyValueList[i] = BigDecimal(0)
                        s.indexOf('.') != s.lastIndexOf('.') -> {
                            while(s.indexOf('.') != s.lastIndexOf('.')) {
                                s.delete(s.lastIndexOf('.'), s.lastIndexOf('.') + 1)
                            } // TODO: make sure this condition can actually be reached if the user presses '.' a bunch of times
                            userCurrencyValueList[i] = BigDecimal(s.toString())
                        }
                        else -> userCurrencyValueList[i] = BigDecimal(s.toString())
                    }
                    if (userCurrencyList[i] == "" || userCurrencyList[i] == ADD_CURRENCY_TEXT) {
                        updateDisplay(i)
                    } else {
                        updateCurrencyValues(i)
                        updateDisplay()
                    }
                    ignoreChange = false
                }
            })
        }
    }

    private fun updateCurrencyList() {
        var nextCurrencyIndex = 0
        for(i in 0 until userCurrencyList.size) {
            if(userCurrencyList[i] != "" && userCurrencyList[i] != ADD_CURRENCY_TEXT) {
                userCurrencyList[nextCurrencyIndex] = userCurrencyList[i]
                userCurrencyValueList[nextCurrencyIndex++] = userCurrencyValueList[i]
            }
        }
        while(nextCurrencyIndex < userCurrencyList.size) {
            userCurrencyList[nextCurrencyIndex] = if (nextCurrencyIndex == 0 || !(userCurrencyList[nextCurrencyIndex - 1] == ADD_CURRENCY_TEXT || userCurrencyList[nextCurrencyIndex - 1] == "")) {
                ADD_CURRENCY_TEXT
            } else {
                ""
            }
            userCurrencyValueList[nextCurrencyIndex++] = BigDecimal.valueOf(0)
        }
    }

    private fun updateCurrencyValues(referenceIndex: Int) {
        val referenceRate = currencyRates[standardCurrency + userCurrencyList[referenceIndex]].toString().toBigDecimal()
        for (i in 0 until userCurrencyValueList.size) {
            if (userCurrencyList[i] != "" && userCurrencyList[i] != ADD_CURRENCY_TEXT) {
                val currencyRate = currencyRates[standardCurrency + userCurrencyList[i]].toString().toBigDecimal()
                userCurrencyValueList[i] = userCurrencyValueList[referenceIndex] / referenceRate * currencyRate
            }
        }
    }

    private fun updateDisplay(index: Int = -1) {
        for (i in 0 until MAX_NUM_CURRENCIES) {
            val targetIndex = if(index == -1) {
                i
            } else {
                index
            }
            val currencyID = resources.getIdentifier("currency$targetIndex", "id", packageName)
            val currencyEntry: TextView = findViewById(currencyID)
            currencyEntry.text = userCurrencyList[targetIndex]

            val valueID = resources.getIdentifier("value$targetIndex", "id", packageName)
            val valueEntry: EditText = findViewById(valueID)
            valueEntry.setText(String.format(Locale.getDefault(), "%.2f", userCurrencyValueList[targetIndex]))

            val linearLayout: LinearLayout = mainGrid.getChildAt(targetIndex) as LinearLayout
            when {
                currencyEntry.text == "" -> linearLayout.visibility = View.GONE
                currencyEntry.text == ADD_CURRENCY_TEXT -> {
                    linearLayout.visibility = View.VISIBLE
                    valueEntry.visibility = View.GONE
                }
                else -> {
                    linearLayout.visibility = View.VISIBLE
                    valueEntry.visibility = View.VISIBLE
                }
            }
            if(index != -1) {
                return
            }
        }
    }
}