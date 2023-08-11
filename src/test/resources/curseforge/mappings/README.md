Without the following args within the test case

```
    "--api-base-url", wm.baseUrl(),
    "--api-key", "test",
```

Started Wiremock and a recording

Set `CF_API_BASE_URL` to "http://localhost:8080"

Ran the test in IntelliJ with `CF_API_KEY` env var set to a real API key.

Copied the mappings into this directory

Removed from each:
- `persistent`
- `scenarioName`
- `requiredScenarioState`
- `insertionIndex`

Added to the mods/files:
- `"transformers": ["response-template"]`
- Changed the `downloadUrl` values to use a response template placeholder