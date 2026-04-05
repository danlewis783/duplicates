**Workflow:**

```
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Xms64m -Xmx512m acme.duplicates.DuplicateFinder.java C:\Foo -o foo.plan
notepad foo.plan          # optional: review/edit before deleting
java acme.duplicates.DuplicateDeleter.java foo.plan --dry-run   # check what would happen
java acme.duplicates.DuplicateDeleter.java foo.plan              # do it
```

---

**Key safety properties of the deleter:**

| Rule                                           | What it guards against                              |
|------------------------------------------------|-----------------------------------------------------|
| KEEP file must exist before any DEL is touched | Plan file edited incorrectly, file moved since scan |
| DEL file re-hashed before delete               | File modified between scan and delete run           |
| Hash mismatch skips, never deletes             | Any doubt at all = no action                        |
| Full log written regardless of dry-run         | Audit trail of every decision                       |
| `--dry-run` flag                               | Sanity check before committing                      |