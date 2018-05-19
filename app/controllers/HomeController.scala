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
import java.io.InputStream
import java.io.FileInputStream
import java.io.File
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database._
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.tasks.Tasks
import java.util.concurrent.ExecutionException


// Controlador de la pagina web
// NOTA: No olvidar poner >>> db: Database como parametro de la clase. OJO!
@Singleton
class HomeController @Inject()(db: Database, cc: ControllerComponents) extends AbstractController(cc)
{

  def verifyIdToken(idToken: String): Boolean = {
    val initialFile = new File("yotearriendo.json");
    val credentials: InputStream = new FileInputStream(initialFile);
    val options = new FirebaseOptions.Builder()
    .setServiceAccount(credentials)
    .setDatabaseUrl("https://yotearriendo-d532f.firebaseio.com/")
    .build();
    FirebaseApp.initializeApp(options);

    try {
      var decodedToken = Tasks.await(FirebaseAuth.getInstance().verifyIdToken(idToken))
      // Token is valid and not revoked.
      var uid = decodedToken.getUid();
      return true      
    } catch {
      case e:Exception=>
      return false
    }
  }

  //var agency:Agency = new Agency("","", "")
  // Metodo para recuperar los datos de la agencia
  def getAgencyInfoService = Action {    
    var result = getAgencyInfoFunction()


    if (result == None){
      // Y retornamos como un json los resultados (info de la agencia)
      BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error!"))
    }else{
      // En caso de error, retornamos un mensaje al respecto
      Ok(Json.toJson(result.get))
    }
  }

  def getAgencyInfoFunction() :Option[Agency] = {
    // Primero creamos una variable para realizar la conexion con la BD
    val conexion = db.getConnection()
    try {
      // Luego creamos una variable en donde formularemos nuestra query SQL de busqueda y la ejecutamos
      val query = conexion.createStatement
      val resultado = query.executeQuery("SELECT * FROM Agency");
      resultado.next() // OJO!!! -> Esta instruccion es necesaria para poder ver/acceder correctamente al resultado

      // Si todo salio bien, entonces creamos un objeto agencia
      var agency = Agency(resultado.getString("nit"), resultado.getString("name"), resultado.getString("description"));

      // Antes de terminar (sea que la consulta sea exitosa o no), cerramos la conexion a la BD
      conexion.close()
      return Some(agency)
    }
    catch {
      // Antes de terminar (sea que la consulta sea exitosa o no), cerramos la conexion a la BD
      // En caso de error, retornamos un mensaje al respecto
      case e: Exception => 
        conexion.close()
        return None
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
      val llaves = cuerpoJson.as[JsObject].keys // Sacamos la lista de claves (keys) de dicho json en un Set (Conjunto)
      
      // Y revisamos que si esten las 4 claves necesarias para realizar la busqueda
      // Por lo tanto, si NO estan todas las claves entonces
      if (!llaves.contains("checkIn") || !llaves.contains("checkOut") || !llaves.contains("city") || !llaves.contains("type"))
      {
        // Abortamos y con un json decimos que faltan parametros
        BadRequest(Json.obj("status" -> "Error", "message" -> "Request no tiene todos los parametros indicados"))
      }
      else // En caso que si esten los parametros entonces
      {
        // Intentamos recuperar las fechas con sus claves correspondientes y las formateamos (casteamos) como Strings
        // Nota: El metodo 'asOpt' trata de parsear el valor a recuperar con el tipo indicado,
        //       Si el tipo coincide con el tipo del valor entonces se retorna Option[Tipo] donde para conseguir el valor propiamente se debe usar .get
        //       En caso que los tipos no coincidan entonces se retorna None
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
          
          // Luego, si se presento un error con el calculo de fechas (se debe obtener 'None') entonces
          if (numDays == None)
          {
            // Se aborta y en un json se dice que las fechas no estan bien escritas
            BadRequest(Json.obj("status" -> "Error", "message" -> "Las fechas no tienen el formato DD/MM/YYYY o DD-MM-YYYY"))
          }
          else if (numDays.get < 0) // Por otro lado, si se obtiene que la diferencia es negativa entonces
          {
            // Tambien aborto ya que eso significa que las fechas no tienen el orden correcto
            BadRequest(Json.obj("status" -> "Error", "message" -> "Las fecha de partida no puede ser anterior a la fecha de llegada!"))
          }
          else if (numDays.get == 0) // O si la diferencia es cero entonces
          {
            // Igualmente aborto porque el hospedaje minimo es de un dia
            BadRequest(Json.obj("status" -> "Error", "message" -> "La reserva debe ser de por lo menos de un dia!"))
          }
          else // Ahora, si el calculo de los dias fue correcto entonces
          {
            // Creo una variable en donde se armara el fragmento de query que indicara que tipos de casas se van a buscar
            // Nota: Esta se inicializa como: '' (comillas vacias) ya que si homeType no tiene un contenido correcto entonces que no se busque nada
            var auxQuery = "`type`= ''"
            
            // Ahora, se intenta recuperar el valor de la clave 'type' como si fuera una lista
            var homeType = (cuerpoJson \ "type").asOpt[List[String]]
            
            // Si 'type' NO es una lista entonces
            if (homeType == None)
            {
              // Trato de recuperar el valor de 'type' como si fuera un String
              var homeType = (cuerpoJson \ "type").asOpt[String]
              
              // Y si DE VERDAD lo ES entonces
              if (!(homeType == None))
              {
                // Se arma el fragmento de query con este unico valor de busqueda
                auxQuery = s"`type`= '${homeType.get}'"
              }
            }
            else // En caso que 'type' SI es una lista entonces
            {
              // Revisamos que la lista no este vacia y SI NO LO ESTA entonces
              if (!(homeType.get.isEmpty))
              {
                // Se arma el fragmento de query de busqueda con cada uno de los tipos de casa definidos en la lista, por lo que...
                auxQuery = s"`type`= '${homeType.get(0)}'" // Se escribe el primer tipo de casa a buscar
                for (cont <- 1 until homeType.get.length) // Y para el resto se le concatena a lo ya escrito con 'OR'
                {
                  auxQuery = auxQuery + s" OR `type`= '${homeType.get(cont)}'"
                }
              }
            }
            
            // Despues, se intenta recuperar el codigo de ciudad
            val cityCode = (cuerpoJson \ "city").asOpt[String]
            
            // De modo que, si este codigo NO es una hilera entonces
            if (cityCode == None)
            {
              // Se aborta y en un json se dice que el parametro 'city' tiene un tipo incorrecto
              BadRequest(Json.obj("status" -> "Error", "message" -> "El tipo del parametro 'city' debe ser String"))
            }
            else // Sino entonces
            {
              // Creo un Json vacio, en el cual se ira construyendo la respuesta final que se va a retornar
              var jsonResponse = Json.obj()
              
              // Luego creamos una variable para realizar la conexion con la BD
              val conexion = db.getConnection()
          
              try
              {
                // Ahora creamos una variable para formular queries SQL
                val query = conexion.createStatement
                
                // Como primer query, vamos a obtener los datos de la agencia y a formatear los mismos a un json
                val resultado1 = query.executeQuery("SELECT * FROM Agency")
                resultado1.next()
                var jsonInfoAgency= Json.obj("nit" -> resultado1.getString("nit"),
                                            "name" -> resultado1.getString("name"),
                                            "description" -> resultado1.getString("description"))
                
                // Una vez que tengamos los datos de la agencia en un json estos se adjuntaran al json de respuesta bajo la clave 'agency'
                jsonResponse = jsonResponse + ("agency" -> jsonInfoAgency)
                
                // Ahora como segunda query, se van a consultar todos los inmuebles que esten en la ciudad indicada y que sean del tipo -o tipos- especificados
                val resultado2 = query.executeQuery(s"""
                  SELECT h.id, h.name, h.description, h.address, h.latitude, h.longitude, c.name as 'city', th.nameType as 'type', h.rating, h.pricePerNight, h.thumbnail
                  FROM `Home` as h
                  JOIN City as c ON c.`code` = h.`city`
                  JOIN TypeHome as th ON th.`idType` = h.`type`
                  WHERE (`city`='${cityCode.get}') AND (${auxQuery});""")
                
                // Despues, se crea un arreglo json vacio el cual se ira rellenando con jsons que tengan los datos de cada una de las casas
                var arrayHomes = JsArray()
                while (resultado2.next()){
                  var jsonAux = Json.obj("id" -> resultado2.getInt("id"),
                                         "name" -> resultado2.getString("name"),
                                         "description" -> resultado2.getString("description"),
                                         "location" -> Json.obj("address" -> resultado2.getString("address"), "latitude" -> resultado2.getString("latitude"), "longitude" -> resultado2.getString("longitude")),
                                         "city" -> resultado2.getString("city"),
                                         "type" -> resultado2.getString("type"),
                                         "rating" -> resultado2.getDouble("rating"),
                                         "totalAmount" -> (numDays.get)*resultado2.getDouble("pricePerNight"),
                                         "pricePerNight" -> resultado2.getDouble("pricePerNight"),
                                         "thumbnail" -> resultado2.getString("thumbnail")
                      )
                  arrayHomes = arrayHomes :+ jsonAux
                }
                
                // Al terminar de rellenar el arreglo de inmuebles, dicho arreglo se adjunta al json de respuesta bajo la clave 'homes'
                jsonResponse = jsonResponse + ("homes" -> arrayHomes)
                
                // Y se retorna el Json de respuesta
                Ok(jsonResponse)
              }
              catch
              {
                // En caso de error, retornamos un mensaje al respecto
                case _: Throwable => BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error, mientras se consultaba la BD!"))
              }
              finally
              {
                // Y antes de terminar (sea que la consulta a la BD sea exitosa o no), cerramos la conexion a la BD
                conexion.close()
              }
            }
          }
        }
      }
    }
  }
  
  // Metodo para calcular el numero de dias entre dos fechas
  def countDays(date1: String, date2: String): Option[Int] = {
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
      
      // Finalmente, calculamos la diferencia de fechas (en dias) y retornamos el resultado (el cual estara "envuelto" como un option)
      val difference = Days.daysBetween(jodaDate1, jodaDate2);
      Some(difference.getDays)
    }
    catch
    {
      case _: Throwable => None // En caso de error, se retorna None -nada- (Como indicativo de que sucedio un error)
    }
  }

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}
