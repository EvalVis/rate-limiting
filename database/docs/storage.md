# Database Storage Format

The database uses a JSON Lines (`.jsonl`) format for persistence. Each record is a single JSON object on a new line.

## Record Schema

### Versioned Record (for Strong Consistency)
To support quorum-based consistency, each record includes a version number.

```json
{
  "key": "string",
  "value": "string",
  "version": long
}
```

- **key**: The unique identifier for the record.
- **value**: The data stored.
- **version**: A monotonically increasing number (assigned by the coordinator) used to resolve conflicts during quorum reads.

### Backward Compatibility
Records without a `version` field are treated as having version `0`. The system will automatically upgrade records to include versions when overwritten in `STRICT` consistency mode.
