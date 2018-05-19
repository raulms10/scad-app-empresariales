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

    "Test verifyIdToken from Unit" in {
      val controller = inject[HomeController]
      val r = controller.verifyIdToken("eyJhbGciOiJSUzI1NiIsImtpZCI6IjEyMDUwYzMxN2ExMjJlZDhlMWZlODdkN2FhZTdlMzk3OTBmNmMwYjQifQ.eyJpc3MiOiJodHRwczovL3NlY3VyZXRva2VuLmdvb2dsZS5jb20veW90ZWFycmllbmRvLWQ1MzJmIiwibmFtZSI6Imp1YW4gZGllZ28gZ29leiBkdXJhbmdvIiwicGljdHVyZSI6Imh0dHBzOi8vbGg2Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tU05xSHhUY244SG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFFeEUvREhNdFo3RmpKTUEvcGhvdG8uanBnIiwiYXVkIjoieW90ZWFycmllbmRvLWQ1MzJmIiwiYXV0aF90aW1lIjoxNTI2Njk3MjIyLCJ1c2VyX2lkIjoibGw0VWFDSEVkVlFYTzlxcGlGTkgzZkRNQ1BjMiIsInN1YiI6ImxsNFVhQ0hFZFZRWE85cXBpRk5IM2ZETUNQYzIiLCJpYXQiOjE1MjY2OTcyMjIsImV4cCI6MTUyNjcwMDgyMiwiZW1haWwiOiJqZGllZ283MTE4QGdtYWlsLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJmaXJlYmFzZSI6eyJpZGVudGl0aWVzIjp7Imdvb2dsZS5jb20iOlsiMTE1NzA3MjU0ODU0MDIwNjk3ODA0Il0sImVtYWlsIjpbImpkaWVnbzcxMThAZ21haWwuY29tIl19LCJzaWduX2luX3Byb3ZpZGVyIjoiZ29vZ2xlLmNvbSJ9fQ.K9L2TN6UIVz7R0URO5VpilJm9OdxcY2nvZUsYo4mKAqdwuno4SvCeA82lE6fbtEa3aEmce1adTWZW25e9QRqXcC0NzHk3Rb_8wKw9c-P36TAlYTNM2l2tkE5EY702GPSH5-j7miCtVGJXAhGHsFXrs571xXv2RTPun2xxM_36IfqpZwJC0HfISWzWRbOQbGXMf2AI8N2D5OtQEgbxl6eJS0VdfsG8-KaEAENLzpY2HGjHF2vQO-YwsD6lSuiiMyXiW0X2PWZ536DbSjFfZgHIhI9NLz4BYEBOj-oEbfQ2jdmvsO9tRDbBuHiUq09-3rWVmOALfL1UGP0gy6ScrDOag")
      r mustBe true
    }
  }
}
