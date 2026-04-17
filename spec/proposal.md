Implement batch job with Spring Batch and JDBC to import temperature data from CSV files into MySQL database:

1. Extract "name", "datetime", and "temp" columns from the csv file, ignore other columns.
2. The "name" and "datetime" columns make a unique pair.
3. The duplicate entries should be reported and ignored.
4. Print the summary, how many records were inserted to the database, and how many duplicates were detected.
5. Use Testcontainers for integration testing (must not use H2).
6. Use Java 21 compatible features. Use Java records instead of POJOs for data. 