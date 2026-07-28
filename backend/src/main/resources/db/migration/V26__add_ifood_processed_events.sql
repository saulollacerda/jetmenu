-- Registry of iFood event ids already handled by the polling loop.
--
-- iFood redelivers events whenever an acknowledgment does not reach the API, and polling
-- windows can overlap, so the same event id may show up more than once. Homologation of the
-- Order module requires detecting and discarding those duplicates instead of reprocessing
-- them (a reprocessed CONCLUDED/CANCELLED would rewrite an order that was already settled).
--
-- Rows are purged after 7 days on every sync run: GET /orders/{id} stops returning details
-- past that window, so an older event id can never come back with anything meaningful.

CREATE TABLE IF NOT EXISTS ifood_processed_events (
    event_id     TEXT      PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);

-- Serves the 7-day purge ("delete ... where processed_at < ?").
CREATE INDEX IF NOT EXISTS idx_ifood_processed_events_processed_at
    ON ifood_processed_events (processed_at);
