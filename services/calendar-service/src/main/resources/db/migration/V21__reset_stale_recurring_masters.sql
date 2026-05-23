-- V21: Reset stale recurring-master events to make them visible in range queries.
--
-- The current application code never sets is_recurring_master = TRUE for new events
-- (setRecurringMaster(true) is never called). Any rows with is_recurring_master = TRUE
-- are legacy data from an older code path. The updated range queries now EXCLUDE master
-- rows (recurringMaster = false is the only displayable form). Reset them so they
-- display correctly as regular events on the client.
UPDATE events
SET is_recurring_master = FALSE
WHERE is_recurring_master = TRUE;
