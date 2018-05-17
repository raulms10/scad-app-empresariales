package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import models.Agency
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "HomeController GET" should {

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }

    "Test getAgencyInfoFunction from Unit" in {
      val controller = inject[HomeController]
      val r = controller.getAgencyInfoFunction()
      r.get.toString mustBe "Agency(1234-4567-00048-6553,Arrendamientos SCAD,Agencia de arrendamientos para estudiantes de la Universidad de Antioquia.)"
    }
  }
}
