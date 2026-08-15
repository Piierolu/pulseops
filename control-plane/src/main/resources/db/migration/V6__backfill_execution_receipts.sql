INSERT INTO check_execution_receipts (execution_id, monitor_id, checked_at, received_at)
SELECT DISTINCT ON (execution_id)
  execution_id,
  monitor_id,
  checked_at,
  checked_at
FROM check_results
ORDER BY execution_id, checked_at DESC
ON CONFLICT (execution_id) DO NOTHING;
