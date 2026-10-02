package com.hesabbeitna.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SearchTest {
    private val data=Household(accounts=listOf(Account("cash","النقد"),Account("bank","حساب البنك")),
        transactions=listOf(Transaction("old",TxType.EXPENSE,12550,LocalDate.now().minusMonths(2).toString(),"cash",categoryId="expense-0",note="إيصال كَهْرَباء"),
            Transaction("transfer",TxType.TRANSFER,8000,today(),"cash",destinationId="bank",note="إلى الادخار")))
    @Test fun findsHistoricTransactionsAndArabicDiacritics(){assertTrue(searchHousehold(data,"ايصال كهرباء").any{it.id=="old"});assertTrue(searchHousehold(data,"إيصال").any{it.id=="old"})}
    @Test fun supportsArabicAndPersianAmounts(){for(query in listOf("125.50","١٢٥٫٥٠","۱۲۵.۵۰"))assertTrue(query,searchHousehold(data,query).any{it.id=="old"})}
    @Test fun transferDestinationIsSearchable(){assertTrue(searchHousehold(data,"البنك").any{it.kind=="transaction"&&it.id=="transfer"})}
    @Test fun findsToolsAndAllTermsMustMatch(){assertTrue(searchHousehold(data,"dark").any{it.kind=="action"&&it.id=="settings"});assertFalse(searchHousehold(data,"كهرباء البنك").any{it.id=="old"})}
    @Test fun capsTransactionsAndReturnsNewestFirst(){val rows=(1..80).map{Transaction("t-$it",TxType.EXPENSE,100,today(),"cash",categoryId="expense-0",note="مراجعة مشتركة",created=it.toLong())};val copy=data.copy(transactions=rows)
        val found=searchHousehold(copy,"مراجعة مشتركة").filter{it.kind=="transaction"};assertEquals(50,found.size);assertEquals("t-80",found.first().id);assertEquals("t-31",found.last().id);assertEquals(rows,copy.transactions)}
    @Test fun emptyQueryReturnsNoData(){assertTrue(searchHousehold(data,"  ").isEmpty());assertTrue(searchHousehold(data,"لاوجودلهذاالنص").isEmpty())}
}
