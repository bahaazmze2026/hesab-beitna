package com.hesabbeitna.app;
import java.time.*;
import java.util.*;
public class FinanceCheck {
    static int checks=0;
    static void eq(Object a,Object b) { checks++; if(!Objects.equals(a,b)) throw new AssertionError(a+" != "+b); }
    static Finance.Entry e(String id,String type,long amount,String account,String dest,String cat,String due) {
        return new Finance.Entry(id,type,amount,"2026-10-02",account,dest,cat,due);
    }
    public static void main(String[] args) {
        Finance.Period oct=Finance.period(LocalDate.of(2026,10,2),1);
        List<Finance.Entry> entries=new ArrayList<>();
        entries.add(e("1","INCOME",500000,"cash",null,"salary",null));
        entries.add(e("2","EXPENSE",120000,"cash",null,"food",null));
        entries.add(e("3","TRANSFER",50000,"cash","wallet",null,null));
        eq(Finance.balance("cash",100000,entries),430000L);
        eq(Finance.balance("wallet",0,entries),50000L);
        eq(Finance.income(entries,oct),500000L); eq(Finance.expense(entries,oct),120000L);
        entries.add(e("4","EXPENSE",1000,"cash",null,"fees",null));
        eq(Finance.expense(entries,oct),121000L);
        entries.set(1,e("2","EXPENSE",100000,"cash",null,"food",null));
        eq(Finance.balance("cash",100000,entries),449000L);
        entries.add(e("5","REFUND",20000,"cash",null,"food",null));
        eq(Finance.expense(entries,oct),81000L); eq(Finance.categories(entries,oct).get("food"),80000L);
        eq(Finance.counts(entries,oct).get("food"),1);
        eq(Finance.excess(125000,100000),25000L); eq(Finance.percent(25000,100000),25.0);
        eq(Finance.percent(1,0),null); eq(Finance.change(150000,120000),25.0);
        eq(Finance.change(150000,0),null);
        eq(Finance.money("125.50"),12550L); eq(Finance.money("١٢٥٫٥٠"),12550L);
        for(String s:new String[]{"-1","1.999","NaN","1e3","","1,000"}) {
            boolean failed=false; try{Finance.money(s);}catch(Exception ex){failed=true;} eq(failed,true);
        }
        Finance.Period pay=Finance.period(LocalDate.of(2026,10,3),25);
        eq(pay.start,LocalDate.of(2026,9,25)); eq(pay.end,LocalDate.of(2026,10,25));
        eq(Finance.period(LocalDate.of(2024,2,29),31).start,LocalDate.of(2024,2,29));
        Finance.Period march=Finance.period(LocalDate.of(2026,3,31),1);
        Finance.Period[] cmp=Finance.comparison(march,LocalDate.of(2026,3,31),1);
        eq(cmp[0].days(),28L); eq(cmp[1].days(),28L);
        eq(cmp[0].end,LocalDate.of(2026,3,29));
        eq(Finance.average(10000,10),1000L);
        eq(Finance.forecast(entries,oct,LocalDate.of(2026,10,1),LocalDate.of(2026,10,5),0),null);
        List<Finance.Entry> forecast=List.of(e("x","EXPENSE",10000,"cash",null,"food",null),e("y","EXPENSE",90000,"cash",null,"rent","due"));
        eq(Finance.forecast(forecast,oct,LocalDate.of(2026,10,1),LocalDate.of(2026,10,10),5000),126000L);
        eq(Finance.buckets(entries,oct,"day").get("2026-10-02"),81000L);
        List<Finance.Entry> frequency=new ArrayList<>();
        for(int i=0;i<10;i++) frequency.add(e("f"+i,"EXPENSE",2000,"cash",null,"a",null));
        for(int i=0;i<2;i++) frequency.add(e("b"+i,"EXPENSE",50000,"cash",null,"b",null));
        eq(Finance.counts(frequency,oct).get("a"),10); eq(Finance.categories(frequency,oct).get("b"),100000L);
        System.out.println("PASS: "+checks+" independent financial acceptance checks");
    }
}
