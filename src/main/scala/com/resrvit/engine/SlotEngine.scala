package com.resrvit.engine

import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.*

/** A time slot on one day. Used for rooms (GD slots, lab/seminar rooms). */
final case class Slot(date: LocalDate, start: LocalTime, end: LocalTime):
  def startAt: LocalDateTime = date.atTime(start)
  def endAt: LocalDateTime   = date.atTime(end)
  def overlaps(other: Slot): Boolean =
    date == other.date && start.isBefore(other.end) && other.start.isBefore(end)

/**
 * Slot-suggestion engine: finds the free slots nearest to the one a user asked for.
 *
 * Exposed with Java collections so the Spring services can call it directly.
 */
object SlotEngine:

  /**
   * Equipment is booked per day. Given how many interchangeable items exist on each day
   * (capacity) and how many are already taken (used), return up to `k` dates in
   * [from, to] that still have a free item, nearest to `target` first. Earlier dates win ties.
   */
  def nearestFreeDays(
      target: LocalDate,
      from: LocalDate,
      to: LocalDate,
      capacityByDate: java.util.Map[LocalDate, Integer],
      usedByDate: java.util.Map[LocalDate, Integer],
      blocked: java.util.Set[LocalDate],
      k: Int
  ): java.util.List[LocalDate] =
    if to.isBefore(from) || k <= 0 then return java.util.List.of()
    val cap  = capacityByDate.asScala
    val used = usedByDate.asScala
    val days = Iterator.iterate(from)(_.plusDays(1)).takeWhile(!_.isAfter(to)).toVector
    days
      .filterNot(d => blocked.contains(d))
      .filter(d => cap.get(d).map(_.intValue).getOrElse(0) - used.get(d).map(_.intValue).getOrElse(0) > 0)
      .sortBy(d => (math.abs(ChronoUnit.DAYS.between(target, d)), d.toEpochDay))
      .take(k)
      .asJava

  /**
   * Rooms: from a list of candidate slots, drop any that overlap a busy slot and return
   * up to `k` of the rest, nearest in time to `target` first.
   */
  def nearestFreeSlots(
      target: Slot,
      candidates: java.util.List[Slot],
      busy: java.util.List[Slot],
      k: Int
  ): java.util.List[Slot] =
    val busyList = busy.asScala.toVector
    candidates.asScala.toVector
      .filterNot(c => busyList.exists(_.overlaps(c)))
      .sortBy(c => (math.abs(ChronoUnit.MINUTES.between(target.startAt, c.startAt)), c.startAt.toString))
      .take(k)
      .asJava

  /** True if `slot` overlaps none of `busy`. */
  def isFree(slot: Slot, busy: java.util.List[Slot]): Boolean =
    !busy.asScala.exists(_.overlaps(slot))
