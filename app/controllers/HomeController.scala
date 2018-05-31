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
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
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
  // CONSTANTES
  val ERROR = 0
  val SUCCESS = 1
  
  // Metodo para exponer el servicio de reservas
  def bookingService = Action {implicit request =>
    // En primer lugar, invocamos la funcion propia de booking y le pasamos tanto el cuerpo del mensaje (este debe ser del tipo Option[JsValue]) como el token de autenticacion (Este ultimo debe ser del tipo Option[String])
    var result = bookingFunction(request.body.asJson, request.headers.get("token"))
    
    // Una vez que se obtenga el resultado de booking
    // Si el resultado es None es porque sucedio algo inesperado
    if (result == None)
    {
      // Y por tanto, retornamos un mensaje al respecto
      BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error!"))
    }
    else // Sino
    {
      // Entonces, simplemente retornamos la respuesta que se obtuvo de la funcion booking
      Ok(result.get)
      
      // NOTA: Si pasa algun otro tipo de error dentro de booking PERO ESTE NO FUE REPENTINO entonces se retorna tal mensaje
    }
  }
  
  // Metodo que realiza la logica (el trabajo) de las reservas
  def bookingFunction(request: Option[JsValue], token: Option[String]) : Option[JsValue] = {
    // Primero, recupero la informacion de la agencia
    var infoAgency = getAgencyInfoFunction()
    
    // Luego, si no se envio nada por el cuerpo de la peticion entonces
    if (request == None) {
      // Se retorna un mensaje de que el json request estaba vacio
      return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "Request vacio!!!"))
    }
    else // En caso que si haya llegado un json con "algo" entonces
    {
      val cuerpoJson = request.get // Recupero el json que llego
      val llaves = cuerpoJson.as[JsObject].keys // Sacamos la lista de claves (keys) de dicho json en un Set (Conjunto)
      
      // Y revisamos que si esten las 3 claves necesarias para realizar la reserva
      // Por lo tanto, si NO estan todas las claves entonces
      if (!llaves.contains("checkIn") || !llaves.contains("checkOut") || !llaves.contains("id"))
      {
        // Abortamos y retornamos un json que indica que faltan parametros
        return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "Request no tiene todos los parametros indicados"))
      }
      else // En caso que si esten los parametros entonces
      {
        // Pasamos a revisar que si tengamos un token de autenticacion
        // Si el token es nulo entonces
        if (token == None)
        {
          // Abortamos y retornamos un json indicando que no se envio ningun token por el encabezado
          return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "No hay ninguna clave token en el encabezado"))
        }
        else // Si el token no es nulo entonces
        {
          // Almacenamos su valor en una variable String
          val authToken = token.get
          
          // Si el token enviado ha sido revocado o no es valido entonces
          if (!verifyIdToken(authToken))
          {
            // Abortamos y retornamos un mensaje de que el token no es valido
            return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "Token de usuario invalido"))
          }
          else // En caso que el token si sea valido entonces
          {
            // Recuperamos el UID del token (identificador unico del usuario y que usaremos para las reservas)
            val client = getUID(authToken)
            
            // Tambien, intentamos recuperar las fechas con sus claves correspondientes y las formateamos (casteamos) como Strings
            // Nota: El metodo 'asOpt' trata de parsear el valor a recuperar con el tipo indicado,
            //       Si el tipo coincide con el tipo del valor entonces se retorna Option[Tipo] donde para conseguir el valor propiamente se debe usar .get
            //       En caso que los tipos no coincidan entonces se retorna None
            val arrivedDate = (cuerpoJson \ "checkIn").asOpt[String]
            val departureDate = (cuerpoJson \ "checkOut").asOpt[String]
            
            // Ahora, si hay problemas con los tipos de las fechas entonces
            if ((arrivedDate == None) || (departureDate == None))
            {
              // Se aborta y en un json se dice que las fechas deben ser hileras
              return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "Las fechas deben ser tipo String"))
            }
            else // Si las fechas tienen el tipo correcto entonces
            {
              // Intento recuperar el id del inmueble que el usuario quiere reservar
              val idHome = (cuerpoJson \ "id").asOpt[Int]
              
              // Si tengo problemas en recuperar tal ID entonces
              if (idHome == None)
              {
                // Aborto y retorno un json indicando que el ID del inmueble debe ser un numero entero
                return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "El ID del inmueble debe ser un numero"))
              }
              else // Si el id del inmueble tiene el tipo correcto entonces
              {
                // Se crea una variable para realizar la conexion con la BD y se crea una variable para formular queries SQL
                val conexion = db.getConnection()
                val query = conexion.createStatement
                
                try
                {
                  // Luego, busco el inmueble con el id especificado
                  val resultado1 = query.executeQuery(s"SELECT COUNT(*) as numHomes FROM Home WHERE id = ${idHome.get};")
                  resultado1.next()
                  
                  // Si la busqueda no arrojo ningun resultado entonces
                  if (resultado1.getInt("numHomes") == 0)
                  {
                    // Aborto y retorno un json que indica que no hay ningun inmueble con el id especificado
                    return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "El ID del inmueble no existe en la BD"))
                  }
                  else // En caso que si exista el inmueble
                  {
                    // Calculo/obtengo el numero de dias de la reserva
                    val numDays = countDays(arrivedDate.get, departureDate.get)
                    
                    // De modo que, si se presento un error con el calculo de fechas (se debe obtener 'None') entonces
                    if (numDays == None)
                    {
                      // Se aborta y en un json se dice que las fechas no estan bien escritas
                      return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "Las fechas no tienen el formato DD/MM/YYYY o DD-MM-YYYY"))
                    }
                    else if (numDays.get < 0) // Por otro lado, si se obtiene que la diferencia es negativa entonces
                    {
                      // Tambien aborto ya que eso significa que las fechas no tienen el orden correcto
                      return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "Las fecha de partida no puede ser anterior a la fecha de llegada!"))
                    }
                    else if (numDays.get == 0) // O si la diferencia es cero entonces
                    {
                      // Igualmente aborto porque el hospedaje minimo es de un dia
                      return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "La reserva debe ser de por lo menos de un dia!"))
                    }
                    else // Ahora, si el calculo de los dias fue correcto entonces
                    {
                      // Recupero todas las reservas asociadas al inmueble deseado
                      // Nota: No se si es por la zona horaria del servidor que aloja la BD o si es por alguna otra configuracion, pero si ejecuto
                      //       la siguiente instruccion, los registros que recupero tienen por una extraña razon las fechas con un dia de adelanto...
                      //       Simplemente MUY RARO... por eso ejecuto la otra instruccion en donde a cada fecha le RESTO un DIA
                      //val resultado2 = query.executeQuery(s"SElECT * FROM Booking WHERE homeId = ${idHome.get};")
                      val resultado2 = query.executeQuery(s"SElECT homeId, DATE_ADD(checkIn, INTERVAL -1 DAY) AS checkIn, DATE_ADD(checkOut, INTERVAL -1 DAY) AS checkOut, idClient, bookingId FROM Booking WHERE homeId = ${idHome.get};")
                      
                      // Posteriormente, creo las dos siguientes variables auxiliares
                      // Estas me permitiran cambiar el orden de las fechas para ajustarme a los formatos de Jodatime y de MySQL
                      val formateadorDMY = new SimpleDateFormat("d-M-y")
                      val formateadorYMD = new SimpleDateFormat("y-M-d")
                      
                      // Tambien creo las siguientes variables, las cuales se usaran para iterar por las fechas de las reservas asociadas al inmueble
                      var auxArrivedDate = ""
                      var auxDepartureDate = ""
                      
                      // Y creo una variable centinela que me indicara si hay solapamiento de intervalos de fechas
                      var fechasSolapadas = false
                      
                      // Ahora, mientras haya reservas asociadas al inmueble Y no se solape ningun intervalo haga
                      while (resultado2.next() && !fechasSolapadas)
                      {
                        // Tomo las fechas de la reserva ya existente (formateandolas como necesita JodaTime)
                        auxArrivedDate = formateadorDMY.format(resultado2.getDate("checkIn"))
                        auxDepartureDate = formateadorDMY.format(resultado2.getDate("checkOut"))
                        
                        // Nota: No prestarle atencion a estas instrucciones
                        //       Estas me ayudaron a encontrar el detallito de las fechas desfasadas
                        /*println(auxArrivedDate)
                        println(auxDepartureDate)
                        println("---------")*/
                        
                        // Y se invoca el metodo dateRangesOverlap para determinar si se solapa la fecha de la reserva que se quiere hacer con alguna reserva ya hecha
                        fechasSolapadas = dateRangesOverlap(arrivedDate.get, departureDate.get, auxArrivedDate, auxDepartureDate)
                      }
                      
                      // Si las fechas de la reserva que se quiere hacer se solapan con alguna otra reserva entonces
                      if (fechasSolapadas)
                      {
                        // Aborto y retorno un mensaje de que no se puede hacer la reserva por solapamiento (Ademas digo cuales son las fecha problema)
                        return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> s"Reserva invalida por fechas solapadas! El inmueble esta ocupado del ${auxArrivedDate} al ${auxDepartureDate}"))
                      }
                      else // Si no hay solapamiento entonces
                      {
                        // Se cambia el formato (orden) de las fechas de la reserva a hacer
                        val checkIn = formateadorYMD.format(formateadorDMY.parse(arrivedDate.get))
                        val checkOut = formateadorYMD.format(formateadorDMY.parse(departureDate.get))
                        
                        // Nota: No prestarle atencion a estas instrucciones
                        //       Estas me ayudaron a encontrar el detallito de las fechas desfasadas
                        /*println(checkIn)
                        println(checkOut)*/
                        
                        // Y se ejecuta la instruccion de reserva (inserccion)
                        // Nota: VUELVE y JUEGA...
                        //       Esta vez cuando inserto el registro, las fechas me les RESTA UN DIA...
                        //       Entonces, en la segunda instruccion TOCA SUMARLE UN DIA para compensar el desfase
                        //val resultadoReserva = query.executeUpdate(s"""INSERT INTO Booking VALUES (${idHome.get}, '${checkIn}', '${checkOut}', "${client}", NULL);""")
                        val resultadoReserva = query.executeUpdate(s"""INSERT INTO Booking VALUES (${idHome.get}, '${checkIn}'+INTERVAL 1 DAY, '${checkOut}'+INTERVAL 1 DAY, "${client}", NULL);""")
                        
                        // Antes de terminar, cierro la conexion con la BD
                        conexion.close()
                        
                        // Y retorno el mensaje de exito en la operacion de reserva
                        return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> SUCCESS, "mensaje" -> "Reserva con exito!!!"))
                      }
                    }
                  }
                }
                catch
                {
                  // En caso de error, retornamos un mensaje al respecto
                  case _: Throwable =>
                    conexion.close() // Antes de terminar (sea que la consulta a la BD sea exitosa o no), cerramos la conexion a la BD
                    return Some(Json.obj("agency" -> infoAgency.get, "codigo" -> ERROR, "mensaje" -> "Hubo un error, mientras se consultaba la BD!"))
                }
              }
            }
          }
        } 
      }
    }
  }
  
  // Metodo para instanciar la conexion con Firebase
  def setFireBaseConnection :Boolean = {
    // En primer lugar, revisamos que instancias de Firebase hay vigentes
    var fbApps = FirebaseApp.getApps()
    
    // Si no hay ninguna instancia de conexion vigente entonces
    if (fbApps.isEmpty)
    {
      // Establecemos los parametros que necesitamos para conectarnos a FireBase
      val initialFile = new File("yotearriendo.json");
      val credentials: InputStream = new FileInputStream(initialFile);
      val options = new FirebaseOptions.Builder()
      .setServiceAccount(credentials)
      .setDatabaseUrl("https://yotearriendo-d532f.firebaseio.com/")
      .build();
      
      // E intentamos inicializar la conexión con Firebase
      FirebaseApp.initializeApp(options);
    }
    
    // Finalmente, retornamos verdadero si en verdad se logro la comunicacion con Firebase o falso en caso contrario
    if (!FirebaseApp.getApps().isEmpty) {
      return true
    } else {
      return false
    }
  }
  
  // Metodo para revisar que un token de Firebase es valido
  def verifyIdToken(idToken: String): Boolean = {
    try
    {
      // En primer lugar, invocamos el metodo de conexion a Firebase
      setFireBaseConnection
      
      // Luego, tratamos de decodificar el token de modo que si puedo recuperar despues el UID entonces retorno verdadero
      var decodedToken = Tasks.await(FirebaseAuth.getInstance().verifyIdToken(idToken))
      var uid = decodedToken.getUid(); // Esto no generara ninguna excepcion si el token es valido y no revocado
      return true      
    }
    catch // En caso de error (lo cual generalmente se da porque el token ha sido revocado) entonces retorno falso
    {
      case e:Exception=>
      return false
    }
  }
  
  // Metodo para obtener el UID dado un token de Firebase
  def getUID(idToken: String): String = {
    try
    {
      // En primer lugar, invocamos el metodo de conexion a Firebase
      setFireBaseConnection
      
      // Luego, tratamos de decodificar el token y retornamos el UID del mismo
      var decodedToken = Tasks.await(FirebaseAuth.getInstance().verifyIdToken(idToken))
      return decodedToken.getUid();
    }
    catch // En caso de error (lo cual generalmente se da porque el token ha sido revocado) entonces retorno ERROR!
    {
      case e:Exception=>
      return "ERROR!"
    }
  }
  
  // Metodo para exponer el servicio de recuperar los datos de la agencia
  def getAgencyInfoService = Action {
    // En primer lugar, tratamos de recuperar los datos de la agencia
    var result = getAgencyInfoFunction()
    
    // Si el resultado es None es porque sucedio algo inesperado
    if (result == None)
    {
      // Y por tanto, retornamos un mensaje al respecto
      BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error!"))
    }
    else // Sino
    {
      // Entonces, simplemente retornamos los datos de la agencia como un Json
      Ok(Json.toJson(result.get))
    }
  }
  
  // Metodo que realiza la logica (el trabajo) de recuperar los datos de la agencia
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
      
      // Y retornamos el objeto agencia
      return Some(agency)
    }
    catch // En caso de error
    {
      // Cerramos la conexion a la BD y retornamos None
      case e: Exception => 
        conexion.close()
        return None
    }
  }
  
  // Metodo para exponer el servicio que recupera todos los inmuebles de la agencia
  def getAllService = Action {
    // En primer lugar, tratamos de recuperar los datos de todos los inmuebles
    var result = getAllFunction
    
    // Si el resultado es None es porque sucedio algo inesperado
    if (result == None)
    {
      // Y por tanto, retornamos un mensaje al respecto
      BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error!"))
    }
    else // Sino
    {
      // Entonces, simplemente retornamos los datos de todos los inmuebles como un Json
      Ok(Json.toJson(result.get))
    }
  }
  
  // Metodo que realiza la logica (el trabajo) para recuperar todos los inmuebles de la agencia
  def getAllFunction :Option[List[Home]] = {
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

      // Ya con nuestros resultados preparados, cerramos la conexion y retornamos los resultados
      conexion.close()
      return Some(arrayHomes)
    }
    catch {
      // En caso de error, cerramos la conexion a la BD y retornamos None
      case e: Exception => 
        conexion.close()
        return None
    }
  }
  
  // Metodo para exponer el servicio de search
  def searchService = Action { implicit request =>
    // En primer lugar, invocamos la funcion propia de search y le pasamos el cuerpo del mensaje (este ultimo debe ser del tipo Option[JsValue])
    var result = searchFunction(request.body.asJson)
    
    // Una vez que se obtenga el resultado de search
    // Si el resultado es None es porque sucedio algo inesperado
    if (result == None)
    {
      // Y por tanto, retornamos un mensaje al respecto
      BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error!"))
    }
    else // Sino
    {
      // Entonces, simplemente retornamos los datos de los inmuebles que trajo la funcion search (Que ya deben estar en un Json)
      Ok(result.get)
      
      // NOTA: Si paso algun otro tipo de error dentro de search PERO QUE NO FUE REPENTINO entonces se retorna tal mensaje
    }
  }
  
  // Metodo para recuperar los inmuebles que concuerdan con los parametros de busqueda del usuario
  def searchFunction(request: Option[JsValue]) : Option[JsValue] = {
    // Primero que todo, si NO llego nada (o sea, None) en el cuerpo del mensaje entonces
    if (request == None) {
      // Retornamos de inmediato y con un json decimos que no recibimos nada
      return Some(Json.obj("status" -> "Error", "message" -> "Request vacio!!!"))
    }
    else // En caso que si haya llegado un json con "algo" entonces
    {
      val cuerpoJson = request.get // Recuperamos el json
      val llaves = cuerpoJson.as[JsObject].keys // Sacamos la lista de claves (keys) de dicho json en un Set (Conjunto)
      
      // Y revisamos que si esten las 4 claves necesarias para realizar la busqueda
      // Por lo tanto, si NO estan todas las claves entonces
      if (!llaves.contains("checkIn") || !llaves.contains("checkOut") || !llaves.contains("city") || !llaves.contains("type"))
      {
        // Abortamos y retornamos un json donde decimos que faltan parametros
        return Some(Json.obj("status" -> "Error", "message" -> "Request no tiene todos los parametros indicados"))
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
          return Some(Json.obj("status" -> "Error", "message" -> "Las fechas deben ser tipo String"))
        }
        else // Si las fechas tienen el tipo correcto entonces
        {
          // Calculo/obtengo el numero de dias que se quiere hospedar el cliente 
          val numDays = countDays(arrivedDate.get, departureDate.get)
          
          // Luego, si se presento un error con el calculo de fechas (se debe obtener 'None') entonces
          if (numDays == None)
          {
            // Se aborta y en un json se dice que las fechas no estan bien escritas
            return Some(Json.obj("status" -> "Error", "message" -> "Las fechas no tienen el formato DD/MM/YYYY o DD-MM-YYYY"))
          }
          else if (numDays.get < 0) // Por otro lado, si se obtiene que la diferencia es negativa entonces
          {
            // Tambien aborto ya que eso significa que las fechas no tienen el orden correcto
            return Some(Json.obj("status" -> "Error", "message" -> "Las fecha de partida no puede ser anterior a la fecha de llegada!"))
          }
          else if (numDays.get == 0) // O si la diferencia es cero entonces
          {
            // Igualmente aborto porque el hospedaje minimo es de un dia
            return Some(Json.obj("status" -> "Error", "message" -> "La reserva debe ser de por lo menos de un dia!"))
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
              return Some(Json.obj("status" -> "Error", "message" -> "El tipo del parametro 'city' debe ser String"))
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
                
                // Antes de terminar (sea que la consulta a la BD sea exitosa o no), cerramos la conexion a la BD
                conexion.close()
                
                // Y se retorna el Json de respuesta
                return Some(jsonResponse)
              }
              catch
              {
                // En caso de error, retornamos un mensaje al respecto
                case _: Throwable =>
                  // Antes de terminar (sea que la consulta a la BD sea exitosa o no), cerramos la conexion a la BD
                  conexion.close()
                  return Some(Json.obj("status" -> "Error", "message" -> "Hubo un error, mientras se consultaba la BD!"))
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
  
  // Metodo para determinar si dos rangos de fechas se solapan
  // Link que me ayudo:
  //   Determine Whether Two Date Ranges Overlap
  //   https://stackoverflow.com/questions/325933/determine-whether-two-date-ranges-overlap?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa
  def dateRangesOverlap(startDateA: String, endDateA: String, startDateB: String, endDateB: String): Boolean = {
    // Para empezar, tokenizamos la 1er fecha del primer intervalo (ya sea que los delimitadores sean: '-' o '/')
    var aux = startDateA.split(Array('-', '/'))
    val day1A = aux(0).toInt       // El 1er token debe ser el dia
    val month1A = aux(1).toInt     // El 2do token debe ser el mes
    val year1A = aux(2).toInt      // El 3er token debe ser el año
    
    // Ya separado correctamente el dia, el mes y el año, creamos un objeto datatime con los datos que obtuvimos
    val jodaDate1A = new DateTime(year1A, month1A, day1A, 0, 0)
    
    // Luego, tokenizamos la 2da fecha del primer intervalo
    aux = endDateA.split(Array('-', '/'))
    val day2A = aux(0).toInt       // El 1er token debe ser el dia
    val month2A = aux(1).toInt     // El 2do token debe ser el mes
    val year2A = aux(2).toInt      // El 3er token debe ser el año
    
    // Nuevamente, creamos un objeto datatime con los datos que obtuvimos
    val jodaDate2A = new DateTime(year2A, month2A, day2A, 0, 0)
    
    // Ahora, tokenizamos la 1er fecha del segundo intervalo
    aux = startDateB.split(Array('-', '/'))
    val day1B = aux(0).toInt       // El 1er token debe ser el dia
    val month1B = aux(1).toInt     // El 2do token debe ser el mes
    val year1B = aux(2).toInt      // El 3er token debe ser el año
    
    // Volvemos a crear un objeto datatime con los datos que obtuvimos
    val jodaDate1B = new DateTime(year1B, month1B, day1B, 0, 0)
    
    // Y, tokenizamos la 2da fecha del segundo intervalo
    aux = endDateB.split(Array('-', '/'))
    val day2B = aux(0).toInt       // El 1er token debe ser el dia
    val month2B = aux(1).toInt     // El 2do token debe ser el mes
    val year2B = aux(2).toInt      // El 3er token debe ser el año
    
    // Y hacemos un ultimo objeto datatime con los datos que obtuvimos
    val jodaDate2B = new DateTime(year2B, month2B, day2B, 0, 0)
    
    // Finalmente, si (la fecha de inicio del 1er intervalo es menor o igual a la fecha de fin del 2do intervalo) y (la fecha de fin del 1er intervalo es mayor o igual a la fecha de inicio del 2do intervalo) entonces
    if ((jodaDate1A.isBefore(jodaDate2B) || jodaDate1A.isEqual(jodaDate2B)) && (jodaDate2A.isAfter(jodaDate1B) || jodaDate2A.isEqual(jodaDate1B)))
    {
      // Retornamos verdadero (o sea, que los intervalos se cruzan, es decir se solapan o al menos se tocan)
      return true
    }
    else // sino
    {
      // Retornamos falso
      return false
    }
  }

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}
