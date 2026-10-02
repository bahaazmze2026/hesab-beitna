package com.hesabbeitna.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** All money is integer Egyptian piastres. Transfers and opening balances are not income. */
public final class Finance {
    private Finance() {}
    public static final class Entry {
        public final String id, type, account, destination, category, due;
        public final long amount;
        public final LocalDate date;
        public Entry(String id, String type, long amount, String date, String account,
                     String destination, String category, String due) {
            this.id=id; this.type=type; this.amount=amount; this.date=LocalDate.parse(date);
            this.account=account; this.destination=destination; this.category=category; this.due=due;
        }
    }
    public static final class Period {
        public final LocalDate start, end;
        public Period(LocalDate start, LocalDate end) { this.start=start; this.end=end; }
        public boolean contains(LocalDate date) { return !date.isBefore(start) && date.isBefore(end); }
        public long days() { return ChronoUnit.DAYS.between(start,end); }
    }
    public static LocalDate anchor(YearMonth month, int salaryDay) {
        if(salaryDay<1 || salaryDay>31) throw new IllegalArgumentException("salary day");
        return month.atDay(Math.min(salaryDay,month.lengthOfMonth()));
    }
    public static Period period(LocalDate date, int salaryDay) {
        YearMonth month=YearMonth.from(date);
        LocalDate start=anchor(month,salaryDay);
        if(date.isBefore(start)) { month=month.minusMonths(1); start=anchor(month,salaryDay); }
        return new Period(start,anchor(month.plusMonths(1),salaryDay));
    }
    /** Both comparison windows have exactly the same number of elapsed days. */
    public static Period[] comparison(Period current, LocalDate asOf, int salaryDay) {
        Period previous=period(current.start.minusDays(1),salaryDay);
        long elapsed=ChronoUnit.DAYS.between(current.start,min(asOf.plusDays(1),current.end));
        long days=Math.max(0,Math.min(elapsed,previous.days()));
        return new Period[]{new Period(current.start,current.start.plusDays(days)),
            new Period(previous.start,previous.start.plusDays(days))};
    }
    public static LocalDate min(LocalDate a,LocalDate b) { return a.isBefore(b)?a:b; }
    public static LocalDate max(LocalDate a,LocalDate b) { return a.isAfter(b)?a:b; }
    public static long money(String input) {
        StringBuilder normalized=new StringBuilder();
        for(char c:input.trim().toCharArray()) {
            if(c>='٠' && c<='٩') normalized.append((char)('0'+c-'٠'));
            else if(c>='۰' && c<='۹') normalized.append((char)('0'+c-'۰'));
            else normalized.append(c=='٫'?'.':c);
        }
        String s=normalized.toString();
        if(!s.matches("[0-9]+(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("اكتب مبلغًا صحيحًا بخانتين عشريتين كحد أقصى");
        long value=new BigDecimal(s).movePointRight(2).longValueExact();
        if(value>100_000_000_000L) throw new IllegalArgumentException("المبلغ يتجاوز الحد المسموح");
        return value;
    }
    public static String format(long value) {
        return String.format(Locale.US,"%,.2f",BigDecimal.valueOf(value,2));
    }
    public static long balance(String account,long opening,List<Entry> entries) {
        long result=opening;
        for(Entry e:entries) {
            if(e.account.equals(account)) {
                long sign=(e.type.equals("INCOME")||e.type.equals("REFUND"))?1:-1;
                result=Math.addExact(result,Math.multiplyExact(sign,e.amount));
            }
            if(e.type.equals("TRANSFER")&&Objects.equals(e.destination,account)) result=Math.addExact(result,e.amount);
        }
        return result;
    }
    public static long income(List<Entry> entries,Period p) {
        long total=0;
        for(Entry e:entries) if(p.contains(e.date)&&e.type.equals("INCOME")) total=Math.addExact(total,e.amount);
        return total;
    }
    public static long expense(List<Entry> entries,Period p) {
        long total=0;
        for(Entry e:entries) if(p.contains(e.date)) {
            if(e.type.equals("EXPENSE")) total=Math.addExact(total,e.amount);
            if(e.type.equals("REFUND")) total=Math.subtractExact(total,e.amount);
        }
        return total;
    }
    public static Map<String,Long> categories(List<Entry> entries,Period p) {
        Map<String,Long> result=new LinkedHashMap<>();
        for(Entry e:entries) if(p.contains(e.date)&&(e.type.equals("EXPENSE")||e.type.equals("REFUND")))
            result.merge(e.category,e.type.equals("EXPENSE")?e.amount:-e.amount,Math::addExact);
        return result;
    }
    public static Map<String,Integer> counts(List<Entry> entries,Period p) {
        Map<String,Integer> result=new LinkedHashMap<>();
        for(Entry e:entries) if(p.contains(e.date)&&e.type.equals("EXPENSE")) result.merge(e.category,1,Integer::sum);
        return result;
    }
    public static Map<String,Long> buckets(List<Entry> entries,Period p,String unit) {
        Map<String,Long> result=new TreeMap<>();
        for(Entry e:entries) if(p.contains(e.date)&&(e.type.equals("EXPENSE")||e.type.equals("REFUND"))) {
            String key=unit.equals("month")?YearMonth.from(e.date).toString():unit.equals("week")?
                e.date.minusDays(e.date.getDayOfWeek().getValue()%7).toString():e.date.toString();
            result.merge(key,e.type.equals("EXPENSE")?e.amount:-e.amount,Math::addExact);
        }
        return result;
    }
    public static Double percent(long part,long whole) { return whole>0?part*100.0/whole:null; }
    public static Double change(long now,long before) { return before>0?(now-before)*100.0/before:null; }
    public static long excess(long spent,long budget) { return Math.max(0,spent-budget); }
    public static long average(long amount,long days) {
        if(days<=0) throw new IllegalArgumentException("No coverage");
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(days),0,RoundingMode.HALF_UP).longValueExact();
    }
    /** For forecasting, recurring payments are omitted from the daily variable rate. */
    public static Long forecast(List<Entry> entries,Period p,LocalDate startTracking,LocalDate today,long outstanding) {
        LocalDate coveredStart=max(p.start,startTracking), coveredEnd=min(p.end,today.plusDays(1));
        long days=ChronoUnit.DAYS.between(coveredStart,coveredEnd);
        if(days<7 || !p.contains(today)) return null;
        Period actual=new Period(coveredStart,coveredEnd);
        boolean hasExpense=false;
        for(Entry e:entries) if(actual.contains(e.date)&&e.type.equals("EXPENSE")) {hasExpense=true;break;}
        if(!hasExpense) return null;
        long variable=0;
        for(Entry e:entries) if(actual.contains(e.date)&&e.due==null) {
            if(e.type.equals("EXPENSE")) variable=Math.addExact(variable,e.amount);
            if(e.type.equals("REFUND")) variable=Math.subtractExact(variable,e.amount);
        }
        long remaining=ChronoUnit.DAYS.between(coveredEnd,p.end);
        long projected=BigDecimal.valueOf(Math.max(0,variable)).multiply(BigDecimal.valueOf(remaining))
            .divide(BigDecimal.valueOf(days),0,RoundingMode.HALF_UP).longValueExact();
        return Math.addExact(expense(entries,actual),Math.addExact(projected,outstanding));
    }
}
