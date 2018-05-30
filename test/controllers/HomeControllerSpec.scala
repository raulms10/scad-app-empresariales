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
      val r = controller.verifyIdToken("eyJhbGciOiJSUzI1NiIsImtpZCI6ImFhNzE5ZDE4MjQ2OTAyN2ZkYWQ5YzVlMjVmNTA0NWUzZjRhZTBjMTAifQ.eyJpc3MiOiJodHRwczovL3NlY3VyZXRva2VuLmdvb2dsZS5jb20veW90ZWFycmllbmRvLWQ1MzJmIiwibmFtZSI6Imp1YW4gZGllZ28gZ29leiBkdXJhbmdvIiwicGljdHVyZSI6Imh0dHBzOi8vbGg2Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tU05xSHhUY244SG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFFeEUvREhNdFo3RmpKTUEvcGhvdG8uanBnIiwiYXVkIjoieW90ZWFycmllbmRvLWQ1MzJmIiwiYXV0aF90aW1lIjoxNTI3Njk3NDI5LCJ1c2VyX2lkIjoibGw0VWFDSEVkVlFYTzlxcGlGTkgzZkRNQ1BjMiIsInN1YiI6ImxsNFVhQ0hFZFZRWE85cXBpRk5IM2ZETUNQYzIiLCJpYXQiOjE1Mjc2OTc0MjksImV4cCI6MTUyNzcwMTAyOSwiZW1haWwiOiJqZGllZ283MTE4QGdtYWlsLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJmaXJlYmFzZSI6eyJpZGVudGl0aWVzIjp7Imdvb2dsZS5jb20iOlsiMTE1NzA3MjU0ODU0MDIwNjk3ODA0Il0sImVtYWlsIjpbImpkaWVnbzcxMThAZ21haWwuY29tIl19LCJzaWduX2luX3Byb3ZpZGVyIjoiZ29vZ2xlLmNvbSJ9fQ.QvMk9w4qJRF9NJOhmNKjGQu228f1W1QgwXQj_NG56kgPZEJq9gwRmvGrPQRCQCa-l0qchqH2zgDZlWUpaFUuSbE3bHmUEnPHDPufpWtTBPRGISg_xI02yMgxgVyzZj_IRyeXKvR7Lds3iKj7_ZJlB8X42me_Q1vOmc8QCGUxU1o7cQbD6CH0DeLFeaAF9vYWLtpupouyU_fJ3UMg86Zm76QDAVkB6sZ59gZnNVIcXcN1UaJ7jnn1SvZemr19vI8XaJVmq3ZDk1B3i_AYVuhX1P---vBd0M9lHlm9MVwOSmEJKaps4F72BtLKi9K3lEvCJbTJE6Pxn-XqSyU8bgvZBg")
      r mustBe Some("jdiego7118@gmail.com")
    }
  }
}
