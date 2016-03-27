package com.galacticfog.gestalt.meta.dcos

import scala.util.{Failure, Success, Try}

/**
 * Simple CLI Menu component. Takes a Map[K,V] where K is the value you want
 * the entry to evaluate to on selection, and V is the string you want to display for
 * the item entry. If used in a real app, it should be extended to take a reference to
 * your console object - this demo will println -> stdout.
 *
 * Usage:
 *
 * // Instantiate menu
 * val menu = SimpleMenu(Map(100 -> "London", 200 -> "Monkey", 300 -> "Nuts"), Some("Select One"))
 *
 * // Draw the menu
 * menu.render()
 *
 * // Prompt user to make a choice
 * value choice = menu.choose()
 *
 */
class DCOSMenu[T](data: Map[T, String], title: Option[String] = None) {

  if (data.isEmpty) throw new RuntimeException("Cannot build menu from empty Map.")

  private val maxwidth = data.values.map(_.size).reduceLeft(_ max _)
  private val menuwidth = maxwidth + data.size.toString.length + 3

  /**
   * Draw the menu with optional title.
   */
  def render() = {
    val keys = data.keys.toSeq

    def mktitle(title: String) = {
      "%s\n%s\n%s".format("-" * menuwidth, title, "-" * menuwidth)
    }

    if (title.isDefined) println(mktitle(title.get))
    keys.indices.iterator.map { i =>
      "%d) %s".format((i + 1), data(keys(i)))
    } foreach println
    this
  }

  /**
   * Get user selection.
   */
  def choose(): T = {
    val ubound = data.size

    // acc could be used to implement 'max_retries'.
    def go(valid: Boolean, acc: Int): T = {
      val choice = Try {
        scala.io.StdIn.readLine(s">> Choose a number [1-$ubound]: ").toInt
      } match {
        case Success(n) => if (1 to ubound contains n) Some(n) else None
        case Failure(_) => None
      }
      if (choice.isDefined) data.keys.toList(choice.get - 1)
      else {
        println(s"Please choose a number between 1 and $ubound.")
        go(false, acc + 1)
      }
    }
    go(false, 1)
  }
}

object DCOSMenu {
  def apply[T](data: Map[T,String], title: Option[String] = None) = {
    new DCOSMenu(data, title)
  }
}
