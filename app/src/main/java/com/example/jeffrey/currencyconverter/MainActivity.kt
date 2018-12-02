package com.example.jeffrey.currencyconverter

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Menu.NONE
import android.view.SubMenu
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URL
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

const val BASE_URL = "http://apilayer.net/api/"
const val LIVE_RATES_ENDPOINT = "live"
const val ACCESS_KEY = "00898df57dadc0ab15e93afe5cc9ff10"
const val MAX_NUM_CURRENCIES = 10
const val ADD_CURRENCY_TEXT = "Add(+)"
const val DEFAULT_CURRENCY_A = "USD"
const val DEFAULT_CURRENCY_B = "EUR"
const val DEFAULT_VALUE = 1

class MainActivity : AppCompatActivity() {

    private lateinit var mainGrid: LinearLayout
    private lateinit var standardCurrency: String
    private var ignoreProgramChange: Boolean = false
    private var userCurrencyList = Array(MAX_NUM_CURRENCIES) {""}
    private var userCurrencyValueList = Array<BigDecimal>(MAX_NUM_CURRENCIES) {BigDecimal.valueOf(0)}
    private val currencyRates = HashMap<String, Any>()
    private val currencyFormat = DecimalFormat("0.00")

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
            linearLayout.setOnClickListener {
                if(userCurrencyList[i] == ADD_CURRENCY_TEXT) {
                    userCurrencyList[i] = DEFAULT_CURRENCY_A
                    userCurrencyValueList[i] = BigDecimal(DEFAULT_VALUE)
                    updateCurrencyList()
                    updateCurrencyValues(0)
                    updateDisplay()
                }
            }
            linearLayout.setOnLongClickListener {
                if( userCurrencyList[i] != ADD_CURRENCY_TEXT) {
                    PopupMenu(linearLayout.context, linearLayout).run {
                        menuInflater.inflate(R.menu.popup_menu, menu)
                        setOnMenuItemClickListener { item ->
                            when(item.itemId) {
                                R.id.changeCurrencyButton -> {
                                    createCurrencyMenu(item.subMenu)
                                }
                                R.id.removeCurrencyButton -> {
                                    userCurrencyList[i] = ""
                                    updateCurrencyList()
                                    updateDisplay()
                                }
                                else -> {
                                    userCurrencyList[i] = item.title.toString()
                                    updateCurrencyValues(0) // TODO: update to currency user last chose
                                    // TODO: if this was the last choice, then its value should just stay the same
                                    updateDisplay()
                                }
                            }
                            true
                        }
                        show()
                    }
                }
                true
            }
        }
    }

    private fun getCurrencyRates() {
        // TODO: maybe change this so it only updates if it's been a certain amount of time since the last update (like 6 hrs)
        try {
            standardCurrency = JSONObject(URL("$BASE_URL$LIVE_RATES_ENDPOINT?access_key=$ACCESS_KEY").readText())["source"].toString()
            val jsonRates = JSONObject(JSONObject(URL("$BASE_URL$LIVE_RATES_ENDPOINT?access_key=$ACCESS_KEY").readText())["quotes"].toString())
            for(key in jsonRates.keys()) {
                currencyRates[key] = jsonRates.get(key) as Any
            }
            /*for((key, value) in currencyRates) {
                // TODO: save rates to file to use in case there's no wifi/data or getting live rates doesn't work for some reason
            }*/
        } catch(e: Exception) { // TODO: handle the error that happens when you run the app with no wifi/data
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
            valueEntry.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            valueEntry.addTextChangedListener(object : TextWatcher {
                var ignoreChange: Boolean = false
                var cursorIndex: Int = 1

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                    if(ignoreProgramChange || ignoreChange) return
                    cursorIndex = valueEntry.selectionStart
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if(ignoreProgramChange || ignoreChange) return
                    cursorIndex += count - before
                    if(count > before) {
                        if(s[0] == '0' && (start == 0 || start == 1)) cursorIndex--
                        cursorIndex = min(cursorIndex, s.length - 1)
                    } else if(count < before) {
                        if(s.isNotEmpty() && s[0] == '.' && start == 0) cursorIndex++
                        else if(s.isNotEmpty() && s[0] == '0' && !s.contains('.')) cursorIndex--
                        cursorIndex = max(0, cursorIndex)
                    }
                }

                override fun afterTextChanged(s: Editable) {
                    if(ignoreProgramChange || ignoreChange) return
                    ignoreChange = true
                    if(s.isEmpty()) s.insert(0, "0")
                    currencyFormat.roundingMode = RoundingMode.DOWN
                    userCurrencyValueList[i] = currencyFormat.format(BigDecimal(s.toString())).toBigDecimal()
                    updateCurrencyValues(i)
                    updateDisplay()
                    valueEntry.setSelection(cursorIndex)
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
            userCurrencyList[nextCurrencyIndex] = (if(nextCurrencyIndex == 0 || !(userCurrencyList[nextCurrencyIndex - 1] == ADD_CURRENCY_TEXT || userCurrencyList[nextCurrencyIndex - 1] == "")) ADD_CURRENCY_TEXT else "")
            userCurrencyValueList[nextCurrencyIndex++] = BigDecimal.valueOf(0)
        }
    }

    private fun updateCurrencyValues(referenceIndex: Int) {
        val referenceRate = currencyRates[standardCurrency + userCurrencyList[referenceIndex]].toString().toBigDecimal()
        for(i in 0 until userCurrencyValueList.size) {
            if(i != referenceIndex && userCurrencyList[i] != "" && userCurrencyList[i] != ADD_CURRENCY_TEXT) {
                val currencyRate = currencyRates[standardCurrency + userCurrencyList[i]].toString().toBigDecimal()
                currencyFormat.roundingMode = RoundingMode.HALF_UP
                userCurrencyValueList[i] = currencyFormat.format(userCurrencyValueList[referenceIndex].divide(referenceRate, currencyFormat.maximumFractionDigits + referenceRate.precision() + currencyRate.precision() + 2, RoundingMode.HALF_UP).multiply(currencyRate)).toBigDecimal()
            }
        }
    }

    private fun updateDisplay(index: Int = -1) {
        ignoreProgramChange = true
        for(i in 0 until MAX_NUM_CURRENCIES) {
            val targetIndex = if(index == -1) i else index
            val currencyID = resources.getIdentifier("currency$targetIndex", "id", packageName)
            val currencyEntry: TextView = findViewById(currencyID)
            currencyEntry.text = userCurrencyList[targetIndex]

            val valueID = resources.getIdentifier("value$targetIndex", "id", packageName)
            val valueEntry: EditText = findViewById(valueID)
            currencyFormat.roundingMode = RoundingMode.UNNECESSARY
            valueEntry.setText(currencyFormat.format(userCurrencyValueList[targetIndex]))

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
            if(index != -1) return
        }
        ignoreProgramChange = false
    }

    private fun createCurrencyMenu(menu: SubMenu?) {
        if(menu != null) {
            menu.clear()
            menu.clearHeader()
            currencyRates.forEach { (key, _) ->
                menu.add(NONE, NONE, NONE, key.subSequence(DEFAULT_CURRENCY_A.length, key.length))
            }
        }
    }
}