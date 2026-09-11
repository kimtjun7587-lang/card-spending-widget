package com.kimtjun.cardspendingwidget;

import java.util.*;
import java.util.regex.*;

/** Conservative KRW-only parser. Ambiguous amounts go to review, never guessed. */
public final class LotteSmsParser {
    private static final Pattern MONEY=Pattern.compile("(?<![\\d,.])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\s*원");
    public static final class Result {
        public final boolean accepted; public final long amount; public final String reason;
        private Result(boolean ok,long amount,String reason){accepted=ok;this.amount=amount;this.reason=reason;}
    }
    static Result reject(String s){return new Result(false,0,s);}
    public static boolean senderMatches(String sender){
        if(sender==null)return false;
        String n=sender.replaceAll("[\\s()\\-]", "");
        return n.equals("15888100")||n.equals("+8215888100");
    }
    public static Result parse(String body){
        if(body==null||body.length()>8000)return reject("빈 문자 또는 너무 긴 문자");
        String b=body.replace('\u00a0',' ').replaceAll("[\\t ]+", " ");
        if(!b.contains("승인")&&!b.contains("취소"))return reject("승인·취소 표시 없음");
        if(Pattern.compile("거절|실패|승인번호|인증번호|취소예정|취소 예정|승인예정|승인 예정|취소요청|취소 요청|취소접수|취소 접수|취소불가|취소 불가|취소방법|취소 방법|자동이체|납부|청구|결제대금|취소되지|승인되지|취소안내|취소 안내").matcher(b).find())
            return reject("거래 확정 문자가 아니거나 지원하지 않는 형식");
        if(Pattern.compile("USD|JPY|EUR|달러|엔화|\\$|해외",Pattern.CASE_INSENSITIVE).matcher(b).find())return reject("해외·외화 결제는 직접 확인 필요");
        List<Long> values=new ArrayList<>();
        Matcher m=MONEY.matcher(b);
        while(m.find()){
            String prefix=b.substring(Math.max(0,m.start()-16),m.start());
            // Ignore labeled totals, but never ignore an unlabeled second amount.
            if(Pattern.compile("(?:누적|잔액|한도|총사용|총 사용|총액|합계)[ :：]*$").matcher(prefix).find())continue;
            try{long value=Long.parseLong(m.group(1).replace(",",""));if(value<=0||value>BudgetEngine.MAX_AMOUNT)return reject("금액 범위 확인 필요");values.add(value);}
            catch(NumberFormatException ex){return reject("금액 형식 확인 필요");}
        }
        if(values.size()!=1)return reject("결제 금액을 하나로 특정할 수 없음");
        boolean cancel=b.contains("취소");
        return new Result(true,cancel?-values.get(0):values.get(0),cancel?"취소 환급":"승인");
    }
}
