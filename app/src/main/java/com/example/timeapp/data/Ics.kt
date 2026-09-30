package com.example.timeapp.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * iCalendar (.ics) exchange — the PDF's "documented event/recurrence subset". Pure; tested in
 * IcsTest. Nothing here syncs with a device calendar: it's file in, file out.
 *
 * **The supported subset**, and nothing else:
 * - VEVENT with SUMMARY, DESCRIPTION, LOCATION (kept as a line of the note), UID, STATUS
 *   (CANCELLED events are skipped), DTSTART with DTEND or DURATION.
 * - Times: UTC (`…Z`), a named zone (`TZID=Europe/London:…`), floating (read in the device's
 *   zone), and all-day dates (`VALUE=DATE`), which become a *task due that day* — T!ME's
 *   calendar holds timed blocks, and an all-day "event" is a deadline, not a block.
 * - RRULE with FREQ=DAILY|WEEKLY|MONTHLY, INTERVAL, UNTIL or COUNT, and BYDAY (weekly only);
 *   EXDATE becomes the rule's skipped dates. Any other rule imports the first occurrence
 *   only, and says so.
 * - Every other property (ATTENDEE, VALARM, RECURRENCE-ID, …) is ignored **and reported**.
 */
object Ics {

    // ---------------------------------------------------------------- export

    private val utcStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** Scheduled blocks (events and task blocks) as VEVENTs in UTC. Due-only tasks have no
     * time to put in a calendar, so they aren't events. */
    fun export(entries: List<Entry>, now: Instant = Instant.now()): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//T!ME//Local export//EN", "CALSCALE:GREGORIAN")
        // A task split into several blocks exports one event per block; the first keeps the
        // entry's UID (so re-importing still recognises it), the rest get their own.
        entries.flatMap { e -> e.blockSpans().mapIndexed { i, span -> Triple(e, i, span) } }.sortedBy { it.third.first }.forEach { (e, i, span) ->
            lines += "BEGIN:VEVENT"
            lines += "UID:${if (i == 0) e.icsUid ?: "entry-${e.id}@time.local" else "entry-${e.id}-block-$i@time.local"}"
            lines += "DTSTAMP:${utcStamp.format(now)}"
            lines += "DTSTART:${utcStamp.format(span.first)}"
            lines += "DTEND:${utcStamp.format(span.second)}"
            lines += "SUMMARY:${escape(e.title)}"
            e.description?.takeIf { it.isNotBlank() }?.let { lines += "DESCRIPTION:${escape(it)}" }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }

    fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

    fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(s[i + 1])
                }
                i += 2
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }

    /** RFC 5545 folding: lines over 75 octets continue on the next line after a space. */
    private fun fold(line: String): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) return line
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var size = 0
        for (ch in line) {
            val n = ch.toString().toByteArray(Charsets.UTF_8).size
            val limit = if (parts.isEmpty()) 75 else 74
            if (size + n > limit) { parts += current.toString(); current = StringBuilder(); size = 0 }
            current.append(ch); size += n
        }
        parts += current.toString()
        return parts.joinToString("\r\n ")
    }

    // ---------------------------------------------------------------- parse

    /** One property line: NAME;PARAM=V;…:value. */
    data class Prop(val name: String, val params: Map<String, String>, val value: String)

    /** A VEVENT's properties, in file order. */
    data class RawEvent(val props: List<Prop>) {
        fun first(name: String) = props.firstOrNull { it.name == name }
        fun all(name: String) = props.filter { it.name == name }
    }

    data class ParseResult(val events: List<RawEvent>, val ignored: Map<String, Int>)

    private val supportedProps = setOf("UID", "SUMMARY", "DESCRIPTION", "LOCATION", "DTSTART", "DTEND", "DURATION", "RRULE", "EXDATE", "STATUS", "DTSTAMP", "CREATED", "LAST-MODIFIED", "SEQUENCE", "TRANSP", "CLASS")

    fun parse(text: String): ParseResult {
        val unfolded = text.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "").lines()
        val events = mutableListOf<RawEvent>()
        val ignored = mutableMapOf<String, Int>()
        var current: MutableList<Prop>? = null
        var nested = 0 // VALARM etc. inside a VEVENT
        for (line in unfolded) {
            if (line.isBlank()) continue
            val prop = parseLine(line) ?: continue
            when {
                prop.name == "BEGIN" && prop.value.equals("VEVENT", true) -> { current = mutableListOf(); nested = 0 }
                prop.name == "END" && prop.value.equals("VEVENT", true) -> { current?.let { events += RawEvent(it) }; current = null }
                current != null && prop.name == "BEGIN" -> { nested++; ignored.merge(prop.value.uppercase(), 1, Int::plus) }
                current != null && prop.name == "END" -> nested--
                current != null && nested == 0 -> {
                    if (prop.name in supportedProps || prop.name.startsWith("X-")) current += prop
                    else ignored.merge(prop.name, 1, Int::plus)
                }
            }
        }
        return ParseResult(events, ignored)
    }

    private fun parseLine(line: String): Prop? {
        // The value starts at the first ':' that isn't inside a quoted parameter.
        var inQuotes = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') inQuotes = !inQuotes
            if (c == ':' && !inQuotes) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(";")
        val params = head.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"')
        }.toMap()
        return Prop(head[0].uppercase(), params, line.substring(colon + 1))
    }

    // ---------------------------------------------------------------- times

    /** A DTSTART/DTEND: either an instant (with the zone it was written in) or a date. */
    sealed class When {
        data class At(val instant: Instant, val zone: ZoneId?) : When()
        data class AllDay(val date: LocalDate) : When()
    }

    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val localFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** Null with a reason when the time can't be read (e.g. an unknown TZID). */
    fun parseWhen(prop: Prop, deviceZone: ZoneId): Pair<When?, String?> {
        val v = prop.value.trim()
        return try {
            when {
                prop.params["VALUE"] == "DATE" || v.length == 8 -> When.AllDay(LocalDate.parse(v, dateFmt)) to null
                v.endsWith("Z") -> When.At(LocalDateTime.parse(v.dropLast(1), localFmt).toInstant(ZoneOffset.UTC), ZoneOffset.UTC) to null
                prop.params["TZID"] != null -> {
                    val zone = runCatching { ZoneId.of(prop.params["TZID"]) }.getOrNull()
                        ?: return null to L("Unknown time zone \"%1\$s\"", prop.params["TZID"])
                    When.At(LocalDateTime.parse(v, localFmt).atZone(zone).toInstant(), zone) to null
                }
                else -> When.At(LocalDateTime.parse(v, localFmt).atZone(deviceZone).toInstant(), null) to null
            }
        } catch (e: java.time.format.DateTimeParseException) {
            null to L("Can't read the time \"%1\$s\"", v)
        }
    }

    /** RFC 5545 DURATION like PT1H30M or P1D. */
    fun parseDuration(v: String): Duration? = runCatching {
        val s = v.trim()
        val negative = s.startsWith("-")
        val body = s.trimStart('+', '-')
        val weeks = Regex("^P(\\d+)W$").find(body)?.groupValues?.get(1)?.toLong()
        val d = if (weeks != null) Duration.ofDays(weeks * 7) else Duration.parse(body)
        if (negative) d.negated() else d
    }.getOrNull()

    // ---------------------------------------------------------------- recurrence

    data class RuleResult(val recurrence: Recurrence?, val note: String?)

    /**
     * RRULE → [Recurrence], or null with a note when the rule is outside the subset (the event
     * then imports its first occurrence only). [firstDate] turns COUNT into an end date.
     */
    fun parseRule(rrule: String, firstDate: LocalDate, exdates: Set<LocalDate>): RuleResult {
        val parts = rrule.split(";").mapNotNull { p -> p.split("=").takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()
        val freq = when (parts["FREQ"]) {
            "DAILY" -> RecurrenceFreq.DAILY
            "WEEKLY" -> RecurrenceFreq.WEEKLY
            "MONTHLY" -> RecurrenceFreq.MONTHLY
            else -> return RuleResult(null, L("Repeats %1\$s T!ME can't follow — only the first occurrence is imported", (parts["FREQ"]?.lowercase() ?: L("in a way"))))
        }
        val unsupported = parts.keys - setOf("FREQ", "INTERVAL", "UNTIL", "COUNT", "BYDAY", "WKST")
        if (unsupported.isNotEmpty() || (freq != RecurrenceFreq.WEEKLY && parts.containsKey("BYDAY"))) {
            return RuleResult(null, L("Repeat rule uses %1\$s — only the first occurrence is imported", ((unsupported + if (freq != RecurrenceFreq.WEEKLY && parts.containsKey("BYDAY")) setOf("BYDAY") else emptySet()).joinToString())))
        }
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val days = parts["BYDAY"]?.split(",")?.mapNotNull { dayOf(it) }?.toSet().orEmpty()
        if (parts["BYDAY"] != null && days.size != parts["BYDAY"]!!.split(",").size) {
            return RuleResult(null, L("Repeat days \"%1\$s\" can't be followed — only the first occurrence is imported", parts["BYDAY"]))
        }
        // RFC 5545 leaves out a month that has no such day (a "31st" skips April), so an
        // imported monthly rule keeps doing exactly that.
        var rule = Recurrence(
            freq, interval, weekdays = days, skipped = exdates,
            monthEnd = if (freq == RecurrenceFreq.MONTHLY) com.example.timeapp.data.MonthEndRule.SKIP_MONTH else com.example.timeapp.data.MonthEndRule.LAST_VALID_DAY,
        )
        val until = parts["UNTIL"]?.take(8)?.let { runCatching { LocalDate.parse(it, dateFmt) }.getOrNull() }
        if (until != null) rule = rule.copy(endOn = until)
        parts["COUNT"]?.toIntOrNull()?.let { count ->
            var d = firstDate
            repeat((count - 1).coerceAtLeast(0)) { d = rule.nextAfter(d, anchor = firstDate) ?: d }
            rule = rule.copy(endOn = d)
        }
        return RuleResult(rule, null)
    }

    private fun dayOf(code: String): DayOfWeek? = when (code.trim().takeLast(2).uppercase()) {
        "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY; "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY; "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY; else -> null
    }.takeIf { code.trim().length == 2 } // "1MO"-style ordinals are outside the subset

    // ---------------------------------------------------------------- import plan

    /** What one VEVENT becomes. [existingId] non-null: an entry with this UID is already here. */
    data class ImportItem(
        val uid: String?,
        val title: String,
        val note: String?,
        val start: Instant?,
        val end: Instant?,
        val dueOn: LocalDate?,
        val recurrence: Recurrence?,
        val sourceZone: ZoneId?,
        val remarks: List<String>,
        val error: String?,
        val existingId: Long?,
    ) {
        val allDay: Boolean get() = dueOn != null && start == null
        val importable: Boolean get() = error == null && (start != null || dueOn != null)
    }

    fun plan(result: ParseResult, existing: List<Entry>, deviceZone: ZoneId): List<ImportItem> {
        val byUid = existing.filter { it.icsUid != null }.associateBy { it.icsUid }
        return result.events.mapNotNull { ev ->
            if (ev.first("STATUS")?.value.equals("CANCELLED", ignoreCase = true)) return@mapNotNull null
            val uid = ev.first("UID")?.value?.trim()
            val title = ev.first("SUMMARY")?.value?.let(::unescape)?.trim().orEmpty().ifEmpty { L("(untitled event)") }
            val note = listOfNotNull(
                ev.first("DESCRIPTION")?.value?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() },
                ev.first("LOCATION")?.value?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }?.let { L("Location: %1\$s", it) },
            ).joinToString("\n\n").ifEmpty { null }
            val remarks = mutableListOf<String>()
            val existingId = uid?.let { byUid[it]?.id }
            val startProp = ev.first("DTSTART") ?: return@mapNotNull ImportItem(uid, title, note, null, null, null, null, null, remarks, L("No start time"), existingId)
            val (start, startError) = parseWhen(startProp, deviceZone)
            if (start == null) return@mapNotNull ImportItem(uid, title, note, null, null, null, null, null, remarks, startError, existingId)
            val exdates = ev.all("EXDATE").flatMap { p -> p.value.split(",").mapNotNull { v -> parseWhen(p.copy(value = v), deviceZone).first } }
                .map { w -> when (w) { is When.AllDay -> w.date; is When.At -> w.instant.atZone(deviceZone).toLocalDate() } }.toSet()
            val firstDate = when (start) { is When.AllDay -> start.date; is When.At -> start.instant.atZone(deviceZone).toLocalDate() }
            val rule = ev.first("RRULE")?.let { parseRule(it.value, firstDate, exdates) }
            rule?.note?.let { remarks += it }
            when (start) {
                is When.AllDay -> {
                    remarks += L("All-day — imported as a task due that day")
                    ImportItem(uid, title, note, null, null, start.date, rule?.recurrence, null, remarks, null, existingId)
                }
                is When.At -> {
                    val end = ev.first("DTEND")?.let { parseWhen(it, deviceZone).first }?.let { (it as? When.At)?.instant }
                        ?: ev.first("DURATION")?.let { parseDuration(it.value) }?.let { start.instant.plus(it) }
                        ?: start.instant.plus(Duration.ofHours(1)).also { remarks += L("No end time — an hour assumed") }
                    if (!end.isAfter(start.instant)) {
                        return@mapNotNull ImportItem(uid, title, note, null, null, null, null, start.zone, remarks, L("Ends before it starts"), existingId)
                    }
                    if (start.zone != null && start.zone != ZoneOffset.UTC && start.zone.normalized() != deviceZone.normalized()) {
                        remarks += L("Written in %1\$s; shown in your time zone", start.zone.id)
                    }
                    ImportItem(uid, title, note, start.instant, end, null, rule?.recurrence, start.zone, remarks, null, existingId)
                }
            }
        }
    }
}
