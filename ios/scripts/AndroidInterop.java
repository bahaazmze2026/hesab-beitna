import com.hesabbeitna.app.BackupCrypto;
import com.hesabbeitna.app.Finance;
import java.nio.file.*;
import java.time.*;
public class AndroidInterop {
 public static void main(String[] args) throws Exception {
  if(args[0].equals("seed")) {
   byte[] json=Files.readAllBytes(Path.of(args[1])); Files.write(Path.of(args[2]),BackupCrypto.encrypt(json,"Mew-Test-123".toCharArray()));
   StringBuilder vectors=new StringBuilder("["); int index=0;
   for(int year=2023;year<=2027;year++)for(int month=1;month<=12;month++)for(int day:new int[]{1,15,28})for(int salary:new int[]{1,25,28,29,30,31}) {LocalDate date=LocalDate.of(year,month,day); Finance.Period p=Finance.period(date,salary); if(index++>0)vectors.append(',');vectors.append(String.format("{\"date\":\"%s\",\"day\":%d,\"start\":\"%s\",\"end\":\"%s\"}",date,salary,p.start,p.end));}
   Files.writeString(Path.of(args[3]),vectors.append(']').toString());
  } else {String json=new String(BackupCrypto.decrypt(Files.readAllBytes(Path.of(args[1])),"Mew-Test-123".toCharArray()),java.nio.charset.StandardCharsets.UTF_8); if(!json.contains("نقد تجريبي")||!json.contains("12345"))throw new AssertionError("Swift backup changed data");System.out.println("PASS: Android -> Swift -> Android AES-GCM/PBKDF2 interoperability");}
 }
}
