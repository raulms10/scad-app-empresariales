package controllers

import javax.inject._
import play.api._
import play.api.mvc._

// Importes necesarios para que esta clase funcione
import play.api.libs.json._
import models.Agency
import models.Booking
import models.Home
import play.api.db._ // Este es especialmente necesario para conectarse con la BD
import org.joda.time.DateTime
import org.joda.time.Days


// Controlador de la pagina web
// NOTA: No olvidar poner >>> db: Database como parametro de la clase. OJO!
@Singleton
class HomeController @Inject()(db: Database, cc: ControllerComponents) extends AbstractController(cc)
{

  // Metodo para recuperar los datos de la agencia
  def getAgencyInfo = Action {    
    // Primero creamos una variable para realizar la conexion con la BD
    val conexion = db.getConnection()

    try {
      // Luego creamos una variable en donde formularemos nuestra query SQL de busqueda y la ejecutamos
      val query = conexion.createStatement
      val resultado = query.executeQuery("SELECT * FROM Agency")
      resultado.next() // OJO!!! -> Esta instruccion es necesaria para poder ver/acceder correctamente al resultado

      // Si todo salio bien, entonces creamos un objeto agencia
      var agency = Agency(resultado.getString("nit"), resultado.getString("name"), resultado.getString("description"))

      // Y retornamos como un json los resultados (info de la agencia)
      Ok(Json.toJson(agency))
    }
    catch {
      // En caso de error, retornamos un mensaje al respecto
      case _: Throwable => BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error!"))
    }
    finally {
      // Antes de terminar (sea que la consulta sea exitosa o no), cerramos la conexion a la BD
      conexion.close()
    }
  }
  
  // Metodo para recuperar todos los inmuebles de la agencia
  def getAll = Action {
    // Primero, se crea una lista vacia para manejar los datos de los inmuebles que lleguen de la BD
    var arrayHomes = List[Home]()

    // Luego creamos una variable para realizar la conexion con la BD
    val conexion = db.getConnection()

    try {
      // Ahora creamos una variable en donde formularemos nuestra query SQL de busqueda y la ejecutamos
      val query = conexion.createStatement
      val resultado = query.executeQuery("SELECT * FROM Home")

      // Si todo salio bien, entonces recorremos cada uno de los registros obtenidos y los vamos convirtiendo a objetos Home, los cuales a su vez se agregan a una lista de apoyo
      while (resultado.next()){
        var aux = Home(resultado.getInt("id"), resultado.getString("name"), resultado.getString("description"), resultado.getString("address"), resultado.getString("latitude"), resultado.getString("longitude"), resultado.getString("city"), resultado.getInt("type"), resultado.getDouble("rating"), resultado.getDouble("pricePerNight"), resultado.getString("thumbnail"), resultado.getString("agencyCode"))
        arrayHomes = arrayHomes :+ aux
      }

      // Ya con nuestros resultados preparados, los tranformamos en formato Json y los retornamos
      val jsonAux = Json.toJson(arrayHomes)
      Ok(jsonAux)
    }
    catch {
      // En caso de error, retornamos un mensaje al respecto
      case _: Throwable => BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error!"))
    }
    finally {
      // Antes de terminar (sea que la consulta sea exitosa o no), cerramos la conexion a la BD
      conexion.close()
    }
  }
  
  // Metodo para recuperar los inmuebles que concuerdan con los parametros de busqueda
  def search = Action {implicit request =>
    // Primero que todo, si NO llego nada (o sea, None) entonces
    if (request.body.asJson == None) {
      // Retornamos de inmediato y con un json decimos que no recibimos nada
      BadRequest(Json.obj("status" -> "Error", "message" -> "Request vacio!!!"))
    }
    else // En caso que si haya llegado un json con "algo" entonces
    {
      val cuerpoJson = request.body.asJson.get // Recuperamos el json
      val llaves = cuerpoJson.as[JsObject].keys // Sacamos la lista de claves de dicho json en un Set (Conjunto)
      
      // Y revisamos que si esten las 4 claves necesarias para realizar la busqueda
      // Por lo tanto, si NO estan todas las claves entonces
      if (!llaves.contains("checkIn") || !llaves.contains("checkOut") || !llaves.contains("city") || !llaves.contains("type"))
      {
        // Abortamos y con un json decimos que faltan parametros
        BadRequest(Json.obj("status" -> "Error", "message" -> "Request no tiene todos los parametros indicados"))
      }
      else // En caso que si esten los parametros entonces
      {
        // Intento recuperar las fechas con sus claves correspondientes y formateandolas como Strings
        // Nota: El metodo 'asOpt' trata de parsear el valor a recuperar con el tipo indicado,
        //       Si el tipo coincide con el tipo del valor entonces se retorna Option[Tipo] donde para conseguir el valor propiamente se debe usar .get
        //       En caso que los tipos no coincidan entonces se retona None
        val arrivedDate = (cuerpoJson \ "checkIn").asOpt[String]
        val departureDate = (cuerpoJson \ "checkOut").asOpt[String]
        
        // Ahora, si hay problemas con los tipos de las fechas entonces
        if ((arrivedDate == None) || (departureDate == None))
        {
          // Se aborta y en un json se dice que las fechas deben ser hileras
          BadRequest(Json.obj("status" -> "Error", "message" -> "Las fechas deben ser tipo String"))
        }
        else // Si las fechas tienen el tipo correcto entonces
        {
          // Calculo/obtengo el numero de dias que se quiere hospedar el cliente 
          val numDays = countDays(arrivedDate.get, departureDate.get)
          
          // En caso que se presente un error con el calculo de fechas y se retorno -1 entonces
          if (numDays == -1)
          {
            // Se aborta y en un json se dice que las fechas no estan bien escritas
            BadRequest(Json.obj("status" -> "Error", "message" -> "Las fechas no tienen el formato DD/MM/YYYY o DD-MM-YYYY"))
          }
          else // Si el calculo de los dias fue correcto entonces
          {
            var auxQuery = "" // Creo una variable auxiliar en donde se escribira el fragmento de busqueda para los tipos de casa que quiere el cliente, por lo que...
          
            try { // Si los tipos de inmueble que quiere el cliente son varios y estan en un arreglo (O sea, una lista) entonces
              var homeType = (cuerpoJson \ "type").as[List[String]] // Recupero y formateo los tipos de casa
              
              if (homeType.isEmpty) // Si la lista esta vacia entonces
              {
                auxQuery = "`type` LIKE '%'" // Se van a buscar: cualquier tipo de casa
              }
              else // Sino entonces
              {
                // Se van a buscar por cada uno de los tipos de casa definidos en la lista, por lo que...
                auxQuery = s"`type`= '${homeType(0)}'" // Se escribe el primer tipo de casa a buscar
                for (cont <- 1 until homeType.length) // Y para el resto se le concatena a lo ya escrito con OR
                {
                  auxQuery = auxQuery + s" OR `type`= '${homeType(cont)}'"
                }
              }
            }
            catch // En caso que el tipo de casa sea uno solo y se haya definido como una hilera entonces
            {
              case _: Throwable =>
                // Se recupera y formatea el tipo de casa como una hilera
                var homeType = (cuerpoJson \ "type").as[String]
                // Y se arma el parametro de busqueda de tipo casa con este unico tipo
                auxQuery = s"`type`= '${homeType}'"
            }
            
            // Ahora, se intenta recuperar el codigo de ciudad
            val cityCode = (cuerpoJson \ "city").asOpt[String]
            
            // De modo que, si este codigo NO es una hilera entonces
            if (cityCode == None)
            {
              // Se aborta y en un json se dice que el parametro 'city' tiene un tipo incorrecto
              BadRequest(Json.obj("status" -> "Error", "message" -> "El tipo del parametro 'city' debe ser String"))
            }
            else // Sino entonces
            {
              // Creo un Json el cual 
              var jsonResponse = Json.obj()
        
              val conexion = db.getConnection()
          
              try{
                val query = conexion.createStatement
                val resultado1 = query.executeQuery("SELECT * FROM Agency")
                resultado1.next()
                
                var jsonInfoAgency= Json.obj("nit" -> resultado1.getString("nit"),
                                            "name" -> resultado1.getString("name"),
                                            "description" -> resultado1.getString("description"))
                
                jsonResponse = jsonResponse + ("agency" -> jsonInfoAgency)
                
                val resultado2 = query.executeQuery(s"""
                  SELECT h.id, h.name, h.description, h.address, h.latitude, h.longitude, c.name as 'city', th.nameType as 'type', h.rating, h.pricePerNight, h.thumbnail
                  FROM `Home` as h
                  JOIN City as c ON c.`code` = h.`city`
                  JOIN TypeHome as th ON th.`idType` = h.`type`
                  WHERE (`city`='${cityCode.get}') AND (${auxQuery});""")
          
                var arrayHomes = JsArray()
                
                while (resultado2.next()){
                  var jsonAux = Json.obj("id" -> resultado2.getInt("id"),
                                         "name" -> resultado2.getString("name"),
                                         "description" -> resultado2.getString("description"),
                                         "location" -> Json.obj("address" -> resultado2.getString("address"), "latitude" -> resultado2.getString("latitude"), "longitude" -> resultado2.getString("longitude")),
                                         "city" -> resultado2.getString("city"),
                                         "type" -> resultado2.getString("type"),
                                         "rating" -> resultado2.getDouble("rating"),
                                         "totalAmount" -> numDays*resultado2.getDouble("pricePerNight"),
                                         "pricePerNight" -> resultado2.getDouble("pricePerNight"),
                                         "thumbnail" -> resultado2.getString("thumbnail")
                      )
                  arrayHomes = arrayHomes :+ jsonAux
                }
                
                jsonResponse = jsonResponse + ("homes" -> arrayHomes)
                Ok(jsonResponse)
              }
              catch {
                // En caso de error, retornamos un mensaje al respecto
                case _: Throwable => BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error!"))
              }
              finally {
                // Antes de terminar (sea que la consulta sea exitosa o no), cerramos la conexion a la BD
                conexion.close()
              }
            }
          }
        }
      }
    }
  }
  
  // Metodo para calcular el numero de dias (valor absoluto) entre dos fechas
  def countDays(date1: String, date2: String): Int = {
    try
    {
      // En primer lugar tokenizamos la 1er fecha (ya sea que los delimitadores sean: '-' o '/')
      var aux = date1.split(Array('-', '/'))
      val day1 = aux(0).toInt // El 1er token debe ser el dia
      val month1 = aux(1).toInt // El 2do token debe ser el mes
      val year1 = aux(2).toInt // El 3er token debe ser el año
      
      // Ahora, creamos un objeto datatime con los datos que obtuvimos de la primera fecha
      val jodaDate1 = new DateTime(year1, month1, day1, 0, 0)
      
      // Igualmente tokenizamos la 2da fecha
      aux = date2.split(Array('-', '/'))
      val day2 = aux(0).toInt // El 1er token debe ser el dia
      val month2 = aux(1).toInt // El 2do token debe ser el mes
      val year2 = aux(2).toInt // El 3er token debe ser el año
      
      // Y creamos otro objeto datatime con los datos que obtuvimos de la segunda fecha
      val jodaDate2 = new DateTime(year2, month2, day2, 0, 0)
      
      // Finalmente, calculamos el valor absoluto de la diferencia de fechas (en dias) y retornamos el resultado
      val difference = Days.daysBetween(jodaDate1, jodaDate2);
      difference.getDays.abs
    }
    catch
    {
      case _: Throwable => -1 // En caso de error se retorna -1 (Como indicativo de que sucedio un error)
    }
  }

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}
