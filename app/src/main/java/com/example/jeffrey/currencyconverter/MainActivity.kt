package com.example.jeffrey.currencyconverter

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
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
//const val CURRENCY_LIST_ENDPOINT = "list" // TODO: pull the full currency names from here to display in the currency-select menu
const val ACCESS_KEY = "00898df57dadc0ab15e93afe5cc9ff10"
const val SAVED_RATES_FILE = "savedRates.txt"
const val SAVED_USER_LIST_FILE = "savedUserList"
const val MAX_NUM_CURRENCIES = 10
const val ADD_CURRENCY_TEXT = "Add [+]"
const val DEFAULT_CURRENCY_A = "USD"
const val DEFAULT_CURRENCY_B = "EUR"
const val DEFAULT_VALUE = 1
const val DELAY_TIME: Long = 5000

class MainActivity : AppCompatActivity() { // TODO: fix long numbers in editText getting cut off slightly on the right when scrolling

    private lateinit var mainGrid: LinearLayout
    private var standardCurrency: String = DEFAULT_CURRENCY_A
    private var ignoreProgramChange: Boolean = false
    private var lastUserChangedCurrency: Int = 0
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
            updateCurrencyValues(lastUserChangedCurrency)
            this@MainActivity.runOnUiThread {
                updateDisplay()
                val valueID = resources.getIdentifier("value0", "id", packageName)
                val valueEntry: EditText = findViewById(valueID)
                valueEntry.setSelection(0)
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
                    updateCurrencyValues(lastUserChangedCurrency)
                    updateDisplay()
                }
            }
            linearLayout.setOnLongClickListener {
                PopupMenu(linearLayout.context, linearLayout).run {
                    if(userCurrencyList[i] == ADD_CURRENCY_TEXT) {
                        menuInflater.inflate(R.menu.clear_menu, menu)
                        setOnMenuItemClickListener { item ->
                            if(item.itemId == R.id.clearAllButton) {
                                for(j in 0 until MAX_NUM_CURRENCIES) {
                                    userCurrencyList[j] = ""
                                }
                                updateCurrencyList()
                                updateDisplay()
                                lastUserChangedCurrency = 0
                            }
                            true
                        }
                    } else {
                        menuInflater.inflate(R.menu.currency_menu, menu)
                        setOnMenuItemClickListener { item ->
                            when(item.itemId) {
                                R.id.changeCurrencyButton -> {
                                    createCurrencyMenu(item.subMenu)
                                }
                                R.id.removeCurrencyButton -> {
                                    userCurrencyList[i] = ""
                                    updateCurrencyList()
                                    updateDisplay()
                                    if(lastUserChangedCurrency == i) {
                                        lastUserChangedCurrency = 0
                                    } else if(lastUserChangedCurrency > i) {
                                        lastUserChangedCurrency--
                                    }
                                }
                                else -> {
                                    userCurrencyList[i] = item.title.toString()
                                    updateCurrencyValues(lastUserChangedCurrency)
                                    updateDisplay()
                                    saveCurrencyList()
                                }
                            }
                            true
                        }
                    }
                    show()
                }
                true
            }
        }
    }

    private fun getCurrencyRates() {
        // TODO: maybe change this so it only updates if it's been a certain amount of time since the last update (like 3 hrs)
        try {
            standardCurrency = JSONObject(URL("$BASE_URL$LIVE_RATES_ENDPOINT?access_key=$ACCESS_KEY").readText())["source"].toString()
            val jsonRates = JSONObject(JSONObject(URL("$BASE_URL$LIVE_RATES_ENDPOINT?access_key=$ACCESS_KEY").readText())["quotes"].toString())
            for(key in jsonRates.keys()) {
                currencyRates[key] = jsonRates.get(key) as Any
            }
            saveCurrencyRates()
        } catch(e: Exception) {
            Log.e("Jeffrey", "Error: " + e.toString())
            if(applicationContext.getFileStreamPath(SAVED_RATES_FILE).exists()) {
                openFileInput(SAVED_RATES_FILE).use {
                    while(it.read() != -1) {
                        var key = ""
                        var value = ""
                        val breakFlag = (-2).toByte()
                        while(true) {
                            val next = it.read()
                            if(next.toByte() == breakFlag || next == -1) {
                                break
                            }
                            key += next.toChar()
                        }
                        while(true) {
                            val next = it.read()
                            if(next.toByte() == breakFlag || next == -1) {
                                break
                            }
                            value += next.toChar()
                        }
                        currencyRates[key] = value
                    }
                    it.close()
                }
            } else {
                this@MainActivity.runOnUiThread {
                    Toast.makeText(this@MainActivity, "Rates unavailable. Retrying in ${DELAY_TIME / 1000}s.", Toast.LENGTH_SHORT).show()
                }
                SystemClock.sleep(DELAY_TIME)
                getCurrencyRates()
            }
        }
    }

    private fun getSavedCurrencyList() {
        val userList: SharedPreferences = getSharedPreferences(SAVED_USER_LIST_FILE, Context.MODE_PRIVATE)
        if(userList.contains("currency0") && userList.getString("currency0", "") != "") {
            for(i in 0 until MAX_NUM_CURRENCIES) {
                if(userList.getString("currency$i", "") != "") {
                    userCurrencyList[i] = userList.getString("currency$i", null)
                }
            }
        } else {
            userCurrencyList[0] = DEFAULT_CURRENCY_A
            userCurrencyList[1] = DEFAULT_CURRENCY_B
        }
        userCurrencyValueList[0] = BigDecimal(DEFAULT_VALUE)
    }

    private fun setInputSettings() { // TODO: fix the error where given [100.00], deleting 1 moves the cursor to |0.00 when the cursor should go to 0|.00
        for(i in 0 until MAX_NUM_CURRENCIES) {
            val valueID = resources.getIdentifier("value$i", "id", packageName)
            val valueEntry: EditText = findViewById(valueID)
            valueEntry.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            valueEntry.addTextChangedListener(object : TextWatcher {
                var ignoreChange: Boolean = false
                var cursorIndex: Int = 1

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                    if(ignoreProgramChange || ignoreChange) {
                        return
                    }
                    cursorIndex = valueEntry.selectionStart
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if(ignoreProgramChange || ignoreChange) {
                        return
                    }
                    cursorIndex += count - before
                    if(count > before) {
                        if(s[0] == '0' && (start == 0 || start == 1)) {
                            cursorIndex--
                        }
                        cursorIndex = min(cursorIndex, s.length - 1)
                    } else if(count < before) {
                        if(s.isNotEmpty() && s[0] == '.' && start == 0) {
                            cursorIndex++
                        } else if(s.isNotEmpty() && s[0] == '0' && !s.contains('.')) {
                            cursorIndex--
                        }
                        cursorIndex = max(0, cursorIndex)
                    }
                }

                override fun afterTextChanged(s: Editable) {
                    if(ignoreProgramChange || ignoreChange) {
                        return
                    }
                    ignoreChange = true
                    if(s.isEmpty()) {
                        s.insert(0, "0")
                    }
                    currencyFormat.roundingMode = RoundingMode.DOWN
                    userCurrencyValueList[i] = currencyFormat.format(BigDecimal(s.toString())).toBigDecimal()
                    updateCurrencyValues(i)
                    updateDisplay()
                    lastUserChangedCurrency = i
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
            userCurrencyList[nextCurrencyIndex] = if(nextCurrencyIndex == 0 || !(userCurrencyList[nextCurrencyIndex - 1] == ADD_CURRENCY_TEXT || userCurrencyList[nextCurrencyIndex - 1] == "")) {
                ADD_CURRENCY_TEXT
            } else {
                ""
            }
            userCurrencyValueList[nextCurrencyIndex++] = BigDecimal.valueOf(0)
        }
        saveCurrencyList()
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
            if(index != -1) {
                return
            }
        }
        ignoreProgramChange = false
    }

    private fun createCurrencyMenu(menu: SubMenu?) { // TODO: implement searching for currencies
        if(menu != null) {
            menu.clear() // TODO: if searching is implemented as a menu item then remove the placeholder item in currency_menu.xml and remove this line
            menu.clearHeader()
            currencyRates.forEach { (key, _) ->
                menu.add(NONE, NONE, NONE, key.subSequence(DEFAULT_CURRENCY_A.length, key.length))
            }
        }
    }

    private fun saveCurrencyRates() {
        openFileOutput(SAVED_RATES_FILE, Context.MODE_PRIVATE).use {
            for((key, value) in currencyRates) {
                val breakFlag = byteArrayOf((-2).toByte())
                it.write( breakFlag + key.toByteArray() + breakFlag + value.toString().toByteArray() + breakFlag)
            }
            it.close()
        }
    }

    private fun saveCurrencyList() {
        val userList: SharedPreferences.Editor = getSharedPreferences(SAVED_USER_LIST_FILE, Context.MODE_PRIVATE).edit()
        for(i in 0 until MAX_NUM_CURRENCIES) {
            if(userCurrencyList[i] == ADD_CURRENCY_TEXT) {
                userList.putString("currency$i", "")
            } else {
                userList.putString("currency$i", userCurrencyList[i])
            }
        }
        userList.apply()
    }
}