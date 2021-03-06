package com.redi.scalademo.presentation

import autowire.{clientCallable, unwrapClientProxy}
import com.redi.scalademo.business._
import com.redi.scalademo.infrastructure.Client
import org.scalajs.dom
import org.scalajs.jquery.{JQuery, JQueryEventObject, jQuery => $}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.math.BigDecimal
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class CalculatorFormListener(
  client: Client,
  numericStringValidator: NumericStringValidator,
  formId:String
) {

  import CalculatorFormListener._

  private val namedElement = "[name]"

  private val formElement: JQuery = $(s"#$formId")
  private val augendElement: JQuery = formElement.find(elementNamed(FormControlNames.Augend))
  private val addendElement: JQuery = formElement.find(elementNamed(FormControlNames.Addend))
  private val summandElement: JQuery = formElement.find(elementNamed(FormControlNames.Summand))
  private val clientSideValidationElement: JQuery =
    formElement.find(elementNamed(FormControlNames.ClientSideValidation))

  def startListening(): Unit = {
    formElement.submit { (e: JQueryEventObject) =>
      onSubmit(e)
    }
    formElement.find(namedElement).on("input", { (self: dom.Element, e: JQueryEventObject) =>
      onInput(self, e)
    }: js.ThisFunction1[dom.Element, JQueryEventObject, js.Any])
  }

  private def elementNamed(name: String): String = {
    s"""[name="$name"]"""
  }

  private def onSubmit(e: JQueryEventObject): Unit = {
    e.preventDefault()
    resetValidationErrors()
    if (clientSideValidationEnabled) {
      val invalidElements: Iterable[JQuery] = getInvalidElements
      if (invalidElements.nonEmpty) {
        for (invalidElement <- invalidElements) {
          addValidationError(invalidElement)
        }
        return
      }
    }
    invokeAddition()
  }

  private def onInput(self: dom.Element, e: JQueryEventObject): Unit = {
    $(self).removeClass(DangerClass)
    summandElement
      .value("")
      .removeClass(InfoClass)
  }

  private def resetValidationErrors(): Unit = {
    formElement.find(namedElement).removeClass(DangerClass)
  }

  private def addValidationError(element: JQuery): Unit = {
    element.addClass(DangerClass)
  }

  private def clientSideValidationEnabled: Boolean = {
    clientSideValidationElement.is(":selected")
  }

  private def getInvalidElements: Iterable[JQuery] = {
    val invalidElements = ArrayBuffer.empty[JQuery]
    for (formControlData: js.Dictionary[String] <- calculableFormData) {
      val name: String = formControlData("name")
      val value: String = formControlData("value")
      val isValid: Boolean = numericStringValidator.isValidNumber(value)
      if (!isValid) {
        invalidElements += formElement.find(elementNamed(name))
      }
    }
    invalidElements
  }

  private def calculableFormData: js.Array[js.Dictionary[String]] = {
    serializeFormData(augendElement) ++ serializeFormData(addendElement)
  }

  private def serializeFormData(element: JQuery): js.Array[js.Dictionary[String]] = {
    element.serializeArray().asInstanceOf[js.Array[js.Dictionary[String]]]
  }

  private def invokeAddition(): Unit = {
    val futureResult: Future[ValidatedResult[BigDecimal]] =
      client[Calculator].add(augendValue, addendValue).call()
    for (result: ValidatedResult[BigDecimal] <- futureResult) {
      result match {
        case Left(validationFailures) =>
          for (validationFailure: ValidationFailure <- validationFailures) {
            formElement
              .find(elementNamed(validationFailure.formControlName))
              .addClass(DangerClass)
          }
        case Right(summand: BigDecimal) =>
          summandElement
            .value(summand.toString)
            .addClass(InfoClass)
      }
    }
  }

  private def augendValue: String = {
    augendElement.value().toString
  }

  private def addendValue: String = {
    addendElement.value().toString
  }

}

object CalculatorFormListener {

  private val InfoClass = "is-info"
  private val DangerClass = "is-danger"

}
