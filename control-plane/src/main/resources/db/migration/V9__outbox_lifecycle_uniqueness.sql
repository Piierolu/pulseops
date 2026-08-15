ALTER TABLE command_outbox
    DROP CONSTRAINT command_outbox_monitor_id_location_scheduled_at_key;

ALTER TABLE command_outbox
    ADD CONSTRAINT uq_command_outbox_monitor_slot_version
    UNIQUE (monitor_id, location, scheduled_at, monitor_version);
