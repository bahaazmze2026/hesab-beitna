package com.hesabbeitna.app;
import java.time.*;
import java.util.*;
public class PropertyCheck {
    static int checks=0;
    static void check(boolean condition) {checks++;if(!condition)throw new AssertionError("invariant "+checks);}
    public static void main(String[] args) {
        for(int year:new int[]{2024,2026})for(int month=1;month<=12;month++)for(int day=1;day<=31;day++) {
            LocalDate date=LocalDate.of(year,month,15);
            Finance.Period p=Finance.period(date,day),n=Finance.period(p.end,day);
            check(p.contains(date));check(p.end.equals(n.start));
            Finance.Period[] cmp=Finance.comparison(p,date,day);
            check(cmp[0].days()==cmp[1].days());check(cmp[0].end.isBefore(date.plusDays(2)));
        }
        Random random=new Random(2252026);
        Finance.Period p=Finance.period(LocalDate.of(2026,10,15),25);
        for(int scenario=0;scenario<200;scenario++) {
            List<Finance.Entry> rows=new ArrayList<>();
            long earned=0,spent=0,refund=0;
            for(int i=0;i<100;i++) {
                long amount=random.nextInt(100_000)+1;
                String type=new String[]{"EXPENSE","INCOME","TRANSFER","REFUND"}[random.nextInt(4)];
                String a=random.nextBoolean()?"cash":"wallet",b=a.equals("cash")?"wallet":"cash";
                rows.add(new Finance.Entry("id-"+i,type,amount,"2026-10-01",a,type.equals("TRANSFER")?b:null,"food",null));
                if(type.equals("INCOME"))earned+=amount;if(type.equals("EXPENSE"))spent+=amount;if(type.equals("REFUND"))refund+=amount;
            }
            check(Finance.balance("cash",10_000,rows)+Finance.balance("wallet",20_000,rows)==30_000+earned-spent+refund);
            check(Finance.expense(rows,p)==spent-refund);check(Finance.income(rows,p)==earned);
        }
        check(Finance.forecast(List.of(),p,p.start,LocalDate.of(2026,10,15),0)==null);
        System.out.println("PASS: "+checks+" date and accounting invariants");
    }
}
