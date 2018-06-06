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
  
  val TESTTOKEN = "123456" // Poner aqui un token valido para hacer las pruebas del Realease # 2 y para el metodo de verifyId
  
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
      r.get mustBe Json.obj("nit" -> "1234-4567-00048-6553","name" -> "Arrendamientos SCAD","description" -> "Agencia de arrendamientos para estudiantes de la Universidad de Antioquia.")
    }
    
    "Prueba para revisar que la funcion de recuperacion de los datos de todos los inmuebles esta funcionando" in {
      val controller = inject[HomeController]
      val r = controller.getAllFunction
      
      if (r == None)
      {
        r mustBe None
      }
      
      val jsonAux = r.get(0)
      val nameHome = (jsonAux \ "name").asOpt[String].get
      
      nameHome mustBe "Las Brisas 2"
    }
    
    "Prueba para revisar que la funcion search - Caso 1: Request vacio" in {
      val controller = inject[HomeController]
      val r = controller.searchFunction(None)
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"status":"Error","message":"Request vacio!!!"}""")
    }
    
    
    "Prueba para revisar que la funcion search - Caso 2: No estan todos los parametros" in {
      val controller = inject[HomeController]
      //val r = controller.searchFunction(Some(Json.obj()))  // Tambien se puede asi, lo que equivale a enviar Some({})
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "city": "CO-MDE",
          "type": "1"
        }
      """)
      val r = controller.searchFunction(Some(jsonAux))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"status":"Error","message":"Request no tiene todos los parametros indicados"}""")
    }
    
    
    "Prueba para revisar que la funcion search - Caso 3: Las fechas deben ser tipo String" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": 7042018,
          "checkOut": 8042018,
          "city": "CO-MDE",
          "type": "1"
        }
      """)
      val r = controller.searchFunction(Some(jsonAux))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"status":"Error","message":"Las fechas deben ser tipo String"}""")
    }
    
    "Prueba para revisar que la funcion search - Caso 4: Las fechas deben tener el formato DD/MM/YYYY o DD-MM-YYYY" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07 04 2018",
          "checkOut": "08~04~2018",
          "city": "CO-MDE",
          "type": "1"
        }
      """)
      val r = controller.searchFunction(Some(jsonAux))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"status":"Error","message":"Las fechas no tienen el formato DD/MM/YYYY o DD-MM-YYYY"}""")
    }
    
    "Prueba para revisar que la funcion search - Caso 5: El parametro city debe ser un string" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "08-04-2018",
          "city": 1234,
          "type": "1"
        }
      """)
      val r = controller.searchFunction(Some(jsonAux))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"status":"Error","message":"El tipo del parametro 'city' debe ser String"}""")
    }
    
    "Prueba para revisar que la funcion search - Caso 6: Fechas invertidas" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "05-04-2018",
          "city": "CO-MDE",
          "type": "1"
        }
      """)
      val r = controller.searchFunction(Some(jsonAux))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"status":"Error","message":"Las fecha de partida no puede ser anterior a la fecha de llegada!"}""")
    }
    
    "Prueba para revisar que la funcion search - Caso 7: Reserva no puede ser de CERO dias" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "07-04-2018",
          "city": "CO-MDE",
          "type": "1"
        }
      """)
      val r = controller.searchFunction(Some(jsonAux))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"status":"Error","message":"La reserva debe ser de por lo menos de un dia!"}""")
    }
    
    "Prueba para revisar que la funcion search - Caso 8: Exito en la operacion" in {
      val controller = inject[HomeController]
      //val r = controller.searchFunction(Some(Json.obj()))  // Tambien se puede asi, lo que equivale a enviar Some({})
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "08-04-2018",
          "city": "CO-MDE",
          "type": ["1", "2"]
        }
      """)
      val r = controller.searchFunction(Some(jsonAux))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"homes":[{"id":1,"name":"Las Brisas 2","description":"40m2, 2 habitaciones y un baño","location":{"address":"Calle 56a #49-70","latitude":"41.40338","longitude":"2.17403"},"city":"Colombia-Medellin","type":"Apartamento","rating":4.2,"totalAmount":100.25,"pricePerNight":100.25,"thumbnail":"https://es.seaicons.com/wp-content/uploads/2016/09/Actions-go-home-icon.png"},{"id":5,"name":"Turmalina","description":"36m2, 4 habitaciones, 1 baño con jacuzzi, luz natural e Internet","location":{"address":"Circular 3 #71-13","latitude":"6.245713","longitude":"-75.590658"},"city":"Colombia-Medellin","type":"Apartamento","rating":1.6,"totalAmount":168.98,"pricePerNight":168.98,"thumbnail":"https://cdn4.iconfinder.com/data/icons/free-large-business-icons/256/Two-storied_house_SH.png"},{"id":8,"name":"REGATA","description":"45.6m2, 6 habitaciones, 2 baños (1 con jacuzzi), terraza, desayuno gratis e Internet","location":{"address":"Carrera 52 #123-95","latitude":"6.309076","longitude":"-75.555521"},"city":"Colombia-Medellin","type":"Casa","rating":4.8,"totalAmount":305.84,"pricePerNight":305.84,"thumbnail":"https://vignette.wikia.nocookie.net/zombiefarm/images/6/66/Log_Cabin.png/revision/latest?cb=20110114173519"}]}""")
    }
    
  }
  
  " ------ Pruebas para las funciones del realase # 2 ------" should {
    
    "Prueba para revisar que se establece conexion con Firebase" in {
      val controller = inject[HomeController]
      val r = controller.setFireBaseConnection
      r mustBe true
    }
    
    "Prueba para revisar que el validador de tokens esta funcionando" in {
      val controller = inject[HomeController]
      val r = controller.verifyIdToken("123456")
      r mustBe None
      //val r = controller.verifyIdToken(TESTTOKEN)
      //r mustBe Some("yatocapo@gmail.com")
    }
    
    "Prueba para revisar el metodo getUID si este trabajando" in {
      val controller = inject[HomeController]
      val r = controller.getUID("123456")
      r mustBe "ERROR!"
    }
    
    "Prueba para revisar que la funcion booking - Caso 1: Request vacio" in {
      val controller = inject[HomeController]
      val r = controller.bookingFunction(None, None) // Este 2do parametro no afecta en nada la prueba
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"Request vacio!!!"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 2: No estan todos los parametros" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "10-04-2018"
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), None) // Este 2do parametro no afecta en nada la prueba
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"Request no tiene todos los parametros indicados"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 3: No se envio token de autenticacion" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "10-04-2018",
          "id": 5
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), None) // El None en este 2do parametro es como no enviar el token
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"No hay ninguna clave token en el encabezado"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 4: Token no valido" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "10-04-2018",
          "id": 5
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), Some("123456"))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"Token de usuario invalido"}""")
    }
    
    
    // ----- Pruebas de booking que requieren un token valido  -----
    /*
    "Prueba para revisar que la funcion booking - Caso 5: Fechas con formato incorrecto (Ej: Numeros)" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": 7042018,
          "checkOut": 10042018,
          "id": 1
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), Some(TESTTOKEN))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"Las fechas deben ser tipo String"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 6: Id de la casa no es un numero" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "10-04-2018",
          "id": "5"
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), Some(TESTTOKEN))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"El ID del inmueble debe ser un numero"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 7: Id de la casa no existe" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "10-04-2018",
          "id": -5
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), Some(TESTTOKEN))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"El ID del inmueble no existe en la BD"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 8: Fechas sin formato correcto" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07 04 2018",
          "checkOut": "10~04~2018",
          "id": 1
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), Some(TESTTOKEN))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"Las fechas no tienen el formato DD/MM/YYYY o DD-MM-YYYY"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 9: Fechas invertidas" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "4-04-2018",
          "id": 1
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), Some(TESTTOKEN))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"Las fecha de partida no puede ser anterior a la fecha de llegada!"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 10: Reserva de CERO DIAS" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "07-04-2018",
          "checkOut": "7-04-2018",
          "id": 1
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), Some(TESTTOKEN))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"La reserva debe ser de por lo menos de un dia!"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 11: Fechas solapadas" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "04-05-2019",
          "checkOut": "21-05-2019",
          "id": 2
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), Some(TESTTOKEN))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":0,"mensaje":"Reserva invalida por fechas solapadas! El inmueble esta ocupado del 15-5-2019 al 19-5-2019"}""")
    }
    
    "Prueba para revisar que la funcion booking - Caso 12: Reserva exitosa" in {
      val controller = inject[HomeController]
      val jsonAux: JsValue = Json.parse("""
        {
          "checkIn": "04-05-2019",
          "checkOut": "21-05-2019",
          "id": 9
        }
      """)
      val r = controller.bookingFunction(Some(jsonAux), Some(TESTTOKEN))
      
      if (r == None)
      {
        r mustBe None
      }
      
      r.get mustBe Json.parse("""{"agency":{"nit":"1234-4567-00048-6553","name":"Arrendamientos SCAD","description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."},"codigo":1,"mensaje":"Reserva con exito!!!"}""")
    }
    */
    // -------------------------------------------------------------
    
    
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
