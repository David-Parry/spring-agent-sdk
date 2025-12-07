/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CommandTriggers(
    @JsonProperty("command_line") Boolean commandLine,
    @JsonProperty("file_watch") List<FileWatchTrigger> fileWatch,
    @JsonProperty("webhooks") List<WebhookTrigger> webhooks,
    @JsonProperty("cron") List<CronTrigger> cron
) {}
