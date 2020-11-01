package org.teckhooi

package object fileutils {
  def prettyPrint(i: Long): String = java.text.NumberFormat.getIntegerInstance.format(i)

}
