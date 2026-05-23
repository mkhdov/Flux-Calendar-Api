import net.fortuna.ical4j.model.Recur;
import java.text.ParseException;
import java.util.Date;
import net.fortuna.ical4j.model.DateList;

public class IcalTest {
    public static void main(String[] args) throws ParseException {
        Recur recur = new Recur("FREQ=WEEKLY;BYDAY=TU,TH");
        System.out.println(recur);
    }
}
