package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import models.Agency
import models.Home
import play.api.test.Helpers._
import play.api.libs.json._

// Clase para ejecutar las pruebas del backend YoTeArriendo de scala
class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  " ------ Pruebas para las funciones del realase # 1 ------" should {
    
    "Prueba inicial para revisar si el index esta funcionando" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }

    "Prueba para revisar que la funcion de recuperacion de los datos de la agencia esta funcionando" in {
      val controller = inject[HomeController]
      val r = controller.getAgencyInfoFunction()
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get.toString mustBe "Agency(1234-4567-00048-6553,Arrendamientos SCAD,Agencia de arrendamientos para estudiantes de la Universidad de Antioquia.)"
    }
    
    "Prueba para revisar que la funcion de recuperacion de los datos de los inmuebles esta funcionando" in {
      val controller = inject[HomeController]
      val r = controller.getAllFunction
      
      if (r == None)
      {
        r mustBe None
      }
      
      val arrayHomes :List[Home] = r.get
      val jsonAux = Json.toJson(arrayHomes(0))
      val nameHome = (jsonAux \ "name").asOpt[String].get
      
      nameHome mustBe "Las Brisas 2"
    }
    
    
    
    
    
    
  }
  
  " ------ Pruebas para las funciones del realase # 2 ------" should {
    
    "Prueba para revisar que el validador de tokens esta funcionando" in {
      val controller = inject[HomeController]
      val r = controller.verifyIdToken("123456")
      r mustBe false
    }
    
    
    
    
  }
  
  " --------- Pruebas para las funciones de apoyo ----------" should {
    
    "Prueba para revisar la funcion countDays - Caso None (Algun delimitador es incorrecto)" in {
      val controller = inject[HomeController]
      val r = controller.countDays("02-05-2020", "21~07~2021")
      
      r mustBe None
    }
    
    "Prueba para revisar la funcion countDays - Caso negativo (La 2da fecha es anterior a la 1er fecha)" in {
      val controller = inject[HomeController]
      val r = controller.countDays("15-05-2020", "5-05-2020")
      
      r.get mustBe -10
    }
    
    "Prueba para revisar la funcion countDays - Caso cero (Las fechas son iguales)" in {
      val controller = inject[HomeController]
      val r = controller.countDays("02-05-2020", "02-05-2020")
      
      r.get mustBe 0
    }
    
    "Prueba para revisar la funcion countDays - Caso positivo (La 2da fecha es posterior a la 1er fecha)" in {
      val controller = inject[HomeController]
      val r = controller.countDays("15-05-2020", "25-05-2020")
      
      r.get mustBe 10
    }
    
    "Prueba para revisar la funcion dateRangesOverlap - Caso 1: Pasa por debajo del limite" in {
      val controller = inject[HomeController]
      val r = controller.dateRangesOverlap("02-02-2019", "14-02-2019", "24-01-2019", "30-01-2019")
      
      r mustBe false
    }
    
    "Prueba para revisar la funcion dateRangesOverlap - Caso 2: Falla - Rosa Limite inferior" in {
      val controller = inject[HomeController]
      val r = controller.dateRangesOverlap("02-02-2019", "14-02-2019", "24-01-2019", "2-02-2019")
      
      r mustBe true
    }
    
    "Prueba para revisar la funcion dateRangesOverlap - Caso 3: Falla - Invade el intervalo por el limite inferior" in {
      val controller = inject[HomeController]
      val r = controller.dateRangesOverlap("02-02-2019", "14-02-2019", "24-01-2019", "8-02-2019")
      
      r mustBe true
    }
    
    "Prueba para revisar la funcion dateRangesOverlap - Caso 4: Falla - Completamente dentro del intervalo 1" in {
      val controller = inject[HomeController]
      val r = controller.dateRangesOverlap("02-02-2019", "14-02-2019", "2-02-2019", "8-02-2019")
      
      r mustBe true
    }
    
    "Prueba para revisar la funcion dateRangesOverlap - Caso 5: Falla - Completamente dentro del intervalo 2" in {
      val controller = inject[HomeController]
      val r = controller.dateRangesOverlap("02-02-2019", "14-02-2019", "5-02-2019", "8-02-2019")
      
      r mustBe true
    }
    
    "Prueba para revisar la funcion dateRangesOverlap - Caso 6: Falla - Completamente dentro del intervalo 3" in {
      val controller = inject[HomeController]
      val r = controller.dateRangesOverlap("02-02-2019", "14-02-2019", "5-02-2019", "14-02-2019")
      
      r mustBe true
    }
    
    "Prueba para revisar la funcion dateRangesOverlap - Caso 7: Falla - Dentro del intervalo supera limite superior" in {
      val controller = inject[HomeController]
      val r = controller.dateRangesOverlap("02-02-2019", "14-02-2019", "5-02-2019", "19-02-2019")
      
      r mustBe true
    }
    
    "Prueba para revisar la funcion dateRangesOverlap - Caso 8: Falla - Casi sale del intervalo pero rosa limite superior" in {
      val controller = inject[HomeController]
      val r = controller.dateRangesOverlap("02-02-2019", "14-02-2019", "14-02-2019", "19-02-2019")
      
      r mustBe true
    }
    
    "Prueba para revisar la funcion dateRangesOverlap - Caso 9: Pasa por encima del limite" in {
      val controller = inject[HomeController]
      val r = controller.dateRangesOverlap("02-02-2019", "14-02-2019", "15-02-2019", "19-02-2019")
      
      r mustBe false
    }
    
  }
  
}
