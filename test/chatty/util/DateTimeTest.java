
package chatty.util;

import chatty.util.DateTime.Formatting;
import static chatty.util.DateTime.MINUTE;
import static chatty.util.DateTime.HOUR;
import static chatty.util.DateTime.DAY;
import static chatty.util.DateTime.S;
import static chatty.util.DateTime.M;
import static chatty.util.DateTime.H;
import static chatty.util.DateTime.D;
import static chatty.util.DateTime.N;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author tduva
 */
public class DateTimeTest {

    /**
     * 
     */
    @Test
    public void testDuration() {
        assertEquals("0s", DateTime.duration(0, 0, 0));
        assertEquals("1m 0s", DateTime.duration(1000*MINUTE, 0, 0));
        assertEquals("1m 1s", DateTime.duration(1000*61, 0, 0));
        assertEquals("1m", DateTime.duration(1000*MINUTE, 1, 0));
        assertEquals("1m", DateTime.duration(1000*MINUTE, 0, 1));
        assertEquals("5h 0m 10s", DateTime.duration((long)1000*60*300+10000, 6, 0));
        assertEquals("30m", DateTime.duration(1000*MINUTE*30, 0, 1));
        assertEquals("1d 1h 5m 0s", DateTime.duration(1000*(HOUR*25+300), 0, 0));
        assertEquals("1d 1h", DateTime.duration(1000*(HOUR*25+300), 2, 0));
        assertEquals("1d 1h 5m", DateTime.duration(1000*(HOUR*25+300), 0, 1));
        assertEquals("1d 1h 5m", DateTime.duration(1000*(HOUR*25+300), 3, 0));
        assertEquals("1h 5m 0s", DateTime.duration(1000*(HOUR +300), 3, 0));
        assertEquals("0m", DateTime.duration(0, 3, 2, 1));
        assertEquals("0h", DateTime.duration(0, 4, 0, M));
        assertEquals("0m", DateTime.duration(0, M, 0, M));
        assertEquals("1m", DateTime.duration(1000*MINUTE, M, 0, M));
        assertEquals("1440m", DateTime.duration(1000*DAY, M, 0, M));
        assertEquals("1440m", DateTime.duration(1000*DAY, M, 0, S));
        assertEquals("1440m", DateTime.duration(1000*(DAY+1), M, 0, S));
        assertEquals("1440m 1s", DateTime.duration(1000*(DAY+1), M, 0, 0));
        assertEquals((DAY+1)+"s", DateTime.duration(1000*(DAY+1), S, 0, 0));
        assertEquals((DAY+1)+"s", DateTime.duration(1000*(DAY+1), S, 0, D));
        assertEquals("24h", DateTime.duration(1000*(DAY+1), H, 1, S));
        assertEquals("24h 0m", DateTime.duration(1000*(DAY+1), H, 0, S));
        assertEquals("24h 1m", DateTime.duration(1000*(DAY+MINUTE), H, 0, S));
        assertEquals("48h 0m", DateTime.duration(1000*(2*DAY), H, 0, S));
        assertEquals("0m", DateTime.duration(1000*10, H, 0, S));
        assertEquals("10s", DateTime.duration(1000*10, H, 0, N));
        assertEquals("", DateTime.duration(0, 0, 0, 0, 0));
        assertEquals("0y 0d 0h 0m 0s", DateTime.duration(0, 0, 0, 0, 0, Formatting.LEADING_ZERO_VALUES));
        assertEquals("0m", DateTime.duration(0, 0, 0, S, 1));
        assertEquals("0s", DateTime.duration(0, S, 0, S, 1));
        assertEquals("0h 0m 0s", DateTime.duration(0, H, 0, N, 3));
        assertEquals("1m 0s", DateTime.duration(1000*MINUTE, N, 0, N, 0));
        assertEquals("1m", DateTime.duration(1000*MINUTE, N, 0, N, 0, Formatting.NO_ZERO_VALUES));
        assertEquals("3 years", DateTime.duration((long)123456789*1000, 1, 0, Formatting.VERBOSE));
//        for (long i=0;i<(1000*DAY*2);i += 33) {
//            assertEquals(DateTime.duration(i, H, 3, 0, LD_ZERO), DateTime.duration3(i/1000, true));
//        }
        for (int i=-5;i<10;i++) {
            for (int y=-5;y<10;y++) {
                for (int k=-5;k<10;k++) {
                    for (int j=-5;j<10;j++) {
                        DateTime.duration(0, i, y, k, j);
                        DateTime.duration(1000*DAY, i, y, k, j);
                        for (int t=-5;t<1000*(DAY+10);t+=1234567) {
                            DateTime.duration(t, i, y, k, j);
                        }
                    }
                }
            }
        }
        
        assertEquals("1h 2m 10s", DateTime.duration(d(0, 1, 2, 10), 0, 0, 0, 0));
        assertEquals("01h 02m 10s", DateTime.duration(d(0, 1, 2, 10), 0, 0, 0, 0, Formatting.DOUBLE_DIGITS));
        assertEquals("1h 02m 10s", DateTime.duration(d(0, 1, 2, 10), 0, 0, 0, 0, Formatting.DOUBLE_DIGITS_EXCEPT_FIRST));
        assertEquals("1:2:10", DateTime.duration(d(0, 1, 2, 10), 0, 0, 0, 0, Formatting.CLOCK_STYLE));
        assertEquals("1:02:10", DateTime.duration(d(0, 1, 2, 10), 0, 0, 0, 0, Formatting.CLOCK_STYLE, Formatting.DOUBLE_DIGITS_EXCEPT_FIRST));
        assertEquals("01:02:10", DateTime.duration(d(0, 1, 2, 10), 0, 0, 0, 0, Formatting.CLOCK_STYLE, Formatting.DOUBLE_DIGITS));
        assertEquals("1h2m10s", DateTime.duration(d(0, 1, 2, 10), 0, 0, 0, 0, Formatting.NO_SPACES));
        assertEquals("1h 02m 10.0s", DateTime.duration(d(0, 1, 2, 10), 0, 0, 0, 0, Formatting.DOUBLE_DIGITS, Formatting.LAST_ONE_EXACT, Formatting.DOUBLE_DIGITS_EXCEPT_FIRST));
        assertEquals("1h 02.1m", DateTime.duration(d(0, 1, 2, 10), 0, 0, S, 0, Formatting.DOUBLE_DIGITS, Formatting.LAST_ONE_EXACT, Formatting.DOUBLE_DIGITS_EXCEPT_FIRST));
        assertEquals("1h 02.1m", DateTime.duration(d(0, 1, 2, 10), 0, 0, S, 0, Formatting.LAST_ONE_EXACT, Formatting.DOUBLE_DIGITS_EXCEPT_FIRST));
        assertEquals("1h 2.1m", DateTime.duration(d(0, 1, 2, 10), 0, 0, S, 0, Formatting.LAST_ONE_EXACT)); // 10 / 60 = 0.16
        assertEquals("1h 2.1m", DateTime.duration(d(0, 1, 2, 11), 0, 0, S, 0, Formatting.LAST_ONE_EXACT)); // 11 / 60 = 0.18
        assertEquals("1h 2.2m", DateTime.duration(d(0, 1, 2, 12), 0, 0, S, 0, Formatting.LAST_ONE_EXACT)); // 12 / 60 = 0.2
        assertEquals("1h 2.2m", DateTime.duration(d(0, 1, 2, 15), 0, 0, S, 0, Formatting.LAST_ONE_EXACT)); // 15 / 60 = 0.25
        assertEquals("1h 2.5m", DateTime.duration(d(0, 1, 2, 30), 0, 0, S, 0, Formatting.LAST_ONE_EXACT)); // 30 / 60 = 0.5
        assertEquals("1h 2.5m", DateTime.duration(d(0, 1, 2, 33), 0, 0, S, 0, Formatting.LAST_ONE_EXACT)); // 33 / 60 = 0.55
        assertEquals("1h 2.5m", DateTime.duration(d(0, 1, 2, 35), 0, 0, S, 0, Formatting.LAST_ONE_EXACT)); // 35 / 60 = 0.58
        assertEquals("1h 2.6m", DateTime.duration(d(0, 1, 2, 36), 0, 0, S, 0, Formatting.LAST_ONE_EXACT)); // 36 / 60 = 0.6
        assertEquals("1h 2.9m", DateTime.duration(d(0, 1, 2, 59), 0, 0, S, 0, Formatting.LAST_ONE_EXACT)); // 59 / 60 = 0.98
        
        assertEquals("1h 9.9m", DateTime.duration(d(0, 1, 9, 59), 0, 0, S, 0, Formatting.LAST_ONE_EXACT));
        assertEquals("01h 09.9m", DateTime.duration(d(0, 1, 9, 59), 0, 0, S, 0, Formatting.LAST_ONE_EXACT, Formatting.DOUBLE_DIGITS));
    }
    
    private static long d(int days, int hours, int minutes, int seconds) {
        return (days*DAY + hours*HOUR + minutes*MINUTE + seconds) * 1000;
    }
    
    @Test
    public void testParseDatetime() {
        assertEquals(1431721017000L, DateTime.parseDatetime("2015-05-15T22:16:57+02:00"));
        assertEquals(1431710836000L, DateTime.parseDatetime("2015-05-15T17:27:16Z"));
        assertEquals(1389375890027L, DateTime.parseDatetime("2014-01-10T17:44:50.027732Z"));
    }
    
    @Test
    public void testParseDuration() {
        assertEquals(60*1000, DateTime.parseDuration("1m"));
        assertEquals(60*1000+1, DateTime.parseDuration("1m 1ms"));
        assertEquals(60, DateTime.parseDurationSeconds("1m 1ms"));
        assertEquals(60*60, DateTime.parseDurationSeconds("1h"));
        assertEquals(65*60, DateTime.parseDurationSeconds("1h 5m"));
        assertEquals(24*60*60*5 + 60*60, DateTime.parseDurationSeconds("5d 1"));
        assertEquals(62, DateTime.parseDurationSeconds("1m 2"));
        assertEquals(30*60*1000, DateTime.parseDuration("30", TimeUnit.MINUTES));
        assertEquals(30*60*1000, DateTime.parseDuration("30m", TimeUnit.MINUTES));
        assertEquals(30*60*1000 + 1000, DateTime.parseDuration("30 1", TimeUnit.MINUTES));
    }
    
}
