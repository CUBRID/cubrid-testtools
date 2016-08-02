public class SP_Sleep {
 
 public static int sleep1(java.lang.Integer sec, java.lang.String nouse) {
  try{
   Thread.sleep(sec*1000);
  } catch(Exception e) {
   e.printStackTrace();
  }
  return sec;
 }

 public static int sleep2(java.lang.Integer sec) {
  try{
   Thread.sleep(sec*1000);
  } catch(Exception e) {
   e.printStackTrace();
  }
  return sec;
 }

}
