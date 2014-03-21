import java.io.*;

/**
 * This class reads the data in an input file.
 * Blank lines and lines starting with the "#" sign are skipped.
 */
class TransactionFileReader
{
  /**
   * The BufferedReader used to read from the file
   */
  private BufferedReader reader;

  /**
   * Creates a new reader reading from the specified input file.
   *
   * @param filename The name of the input file.
   */
  TransactionFileReader(String filename)
  {
    try {
      reader = new BufferedReader(new FileReader(filename));
    } catch (IOException ioe) {
      System.err.println("Input file not found.");
      System.exit(1);
    }
  }

  /**
   * Reads the next non-blank, non-comment line from the input file.
   *
   * @return The next non-blank, non-comment line, or null if the end of file is reached.
   */
  String readLine()
  {
    try {
      String line = reader.readLine();
      if (line != null) {
        line = line.trim();
        if (line.startsWith("#") || line.isEmpty())
          line = readLine();
      }
      return line;
    } catch (IOException ioe) {
      ioe.printStackTrace();
      return null;
    }
  }

  /**
   * Closes the input file.
   */
  void close()
  {
    try {
      reader.close();
    } catch (IOException ioe) {
    }
  }
}
