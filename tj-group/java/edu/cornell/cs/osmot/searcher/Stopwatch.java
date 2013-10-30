package edu.cornell.cs.osmot.searcher;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * An object that measures elapsed time in nanoseconds. Note that measurement
 * overhead is typically on the order of a microsecond (1000 ns) or more.
 *
 * <p>This class is not thread-safe.
 *
 *<p> Basic usage:
 * <pre>
 *   Stopwatch stopwatch = new Stopwatch().{@link #start()};
 *
 *   long millis = stopwatch.{@link #elapsedMillis()};
 *   long nanos  = stopwatch.{@link #elapsedTime}(TimeUnit.NANOSECONDS);
 *      // Measurement accuracy is really only to millis, but if you want ...
 *
 *   String formatted = stopwatch.{@link #toString()};  // e.g. "1.234 ms" or "23.45 s"
 *
 *   stopwatch.{@link #stop()};
 *   stopwatch.{@link #reset()}; // Resets the elapsed time to zero, stops the stopwatch.
 * </pre>
 *
 * <p>Note that it is an error to start or stop a Stopwatch that is already
 * started or stopped respectively.
 * 
 * <p>When testing code that uses this class, use the
 * {@linkplain #Stopwatch(Ticker) alternate constructor} to supply a fake or
 * mock ticker, such as {@link com.google.common.testing.FakeTicker}. This
 * allows you to simulate any valid behavior of the stopwatch.
 *
 * @author kevinb@google.com (Kevin Bourrillion)
 */
public final class Stopwatch {
	
  private long startTick;
  private long lastTick;
  private ArrayList<String> output = new ArrayList<String>();
  private ArrayList<Long> time = new ArrayList<Long>();
  /**
   * Creates (but does not start) a new stopwatch using {@link System#nanoTime}
   * as its time source.
   */
  public Stopwatch() {
	  startTick = System.nanoTime();
	  lastTick = startTick;
  }

  public void addPoint(String description) {
	  time.add(System.nanoTime() - lastTick);
	  output.add(description);
	  lastTick = System.nanoTime();
  }
  
  public String summary() {
	  long total = System.nanoTime() - startTick;
	  String retVal = "";
	  time.add(total);
	  output.add("Total");
	  for (int i = 0; i < time.size(); i++) {
		  double percentage = (double)time.get(i)/total;
		  String output = new DecimalFormat("#.##%").format(percentage); 
		  retVal += this.output.get(i) + ": ";
		  retVal += toString(time.get(i), 4);
		  retVal += " (" + output + ")\n";
	  }
	  return retVal;
  }


  /**
   * Returns a string representation of the current elapsed time, choosing an
   * appropriate unit and using the specified number of significant figures.
   * For example, at the instant when {@code elapsedTime(NANOSECONDS)} would
   * return {1234567}, {@code toString(4)} returns {@code "1.235 ms"}.
   */
  public String toString(Long nanos, int significantDigits) {
    TimeUnit unit = chooseUnit(nanos);
    double value = (double) nanos / NANOSECONDS.convert(1, unit);

    // Too bad this functionality is not exposed as a regular method call
    return String.format("%." + significantDigits + "g %s",
        value, abbreviate(unit));
  }

  private static TimeUnit chooseUnit(long nanos) {
    if (SECONDS.convert(nanos, NANOSECONDS) > 0) {
      return SECONDS;
    }
    if (MILLISECONDS.convert(nanos, NANOSECONDS) > 0) {
      return MILLISECONDS;
    }
    if (MICROSECONDS.convert(nanos, NANOSECONDS) > 0) {
      return MICROSECONDS;
    }
    return NANOSECONDS;
  }

  private static String abbreviate(TimeUnit unit) {
    switch (unit) {
      case NANOSECONDS:
        return "ns";
      case MICROSECONDS:
        return "\u03bcs"; // μs
      case MILLISECONDS:
        return "ms";
      case SECONDS:
        return "s";
      default:
        throw new AssertionError();
    }
  }
}

