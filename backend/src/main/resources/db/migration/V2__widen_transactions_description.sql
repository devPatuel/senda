-- API contract allows descriptions up to 500 chars; V1 created the column as VARCHAR(255)
ALTER TABLE transactions
    ALTER COLUMN description TYPE VARCHAR(500);
