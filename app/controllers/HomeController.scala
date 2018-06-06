package controllers

// Importes necesarios para que esta clase funcione
import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import models.{Agency, Booking, Home}
import play.api.db._ // Este es especialmente necesario para conectarse con la BD
import org.joda.time.{DateTime, Days}
import java.util.{Date, Calendar, TimeZone}
import java.text.SimpleDateFormat
import java.io.{InputStream, FileInputStream, File}
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.firebase.database._
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.tasks.Tasks
import java.util.concurrent.ExecutionException
import scala.collection.mutable.ListBuffer

// Controlador de la pagina web
// NOTA: No olvidar poner >>> db: Database como parametro de la clase. OJO!
@Singleton
class HomeController @Inject()(db: Database, cc: ControllerComponents) extends AbstractController(cc)
{
  // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  // +++++++++++++++++++++      CONSTANTES       ++++++++++++++++++++++
  // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  val errorCode = 0
  val successCode = 1
  // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  
  // ##################################################################
  // ######################      FUNCIONES       ######################
  // ##################################################################
  
  // Release No. 1 ----------------------------------------------------
  
  // Metodo que realiza la logica (el trabajo) de recuperar los datos de la agencia
  // Entrada: Ninguna
  // Salida: Json con los datos de la agencia
  def getAgencyInfoFunction() :Option[JsValue] = {
    // Primero creamos una variable para realizar la conexion con la BD
    val conexion = db.getConnection()
    
    try {
      // Luego creamos una variable en donde formularemos nuestra query SQL de busqueda y la ejecutamos
      val query = conexion.createStatement
      val resultado = query.executeQuery("SELECT nit, name, description FROM Agency");
      resultado.next() // OJO!!! -> Esta instruccion es necesaria para poder ver/acceder correctamente al resultado

      // Si todo salio bien, entonces creamos un objeto agencia
      val agency = Agency(resultado.getString("nit"), resultado.getString("name"), resultado.getString("description"));

      // Antes de terminar (sea que la consulta sea exitosa o no), cerramos la conexion a la BD
      conexion.close()
      
      // Y retornamos el objeto json con los datos de la agencia
      Some(Json.toJson(agency))
    }
    catch // En caso de error
    {
      // Cerramos la conexion a la BD y retornamos None
      case e: Exception => 
        conexion.close()
        None
    }
  }
  
  // Metodo que realiza la logica (el trabajo) para recuperar todos los inmuebles de la agencia
  // Entrada: Ninguna
  // Salida: Json con los datos de los inmuebles
  def getAllFunction :Option[JsValue] = {
    // Primero, se crea una lista vacia para manejar los datos de los inmuebles que lleguen de la BD
    var arrayHomes = ListBuffer[Home]()

    // Luego creamos una variable para realizar la conexion con la BD
    val conexion = db.getConnection()

    try {
      // Ahora creamos una variable en donde formularemos nuestra query SQL de busqueda y la ejecutamos
      val query = conexion.createStatement
      val resultado = query.executeQuery("SELECT id, name, description, address, latitude, longitude, city, type, rating, pricePerNight, thumbnail, agencyCode FROM Home")

      // Si todo salio bien, entonces recorremos cada uno de los registros obtenidos y los vamos convirtiendo a objetos Home, los cuales a su vez se agregan a una lista de apoyo
      while (resultado.next()){
        val aux = Home(resultado.getInt("id"), resultado.getString("name"), resultado.getString("description"), resultado.getString("address"), resultado.getString("latitude"), resultado.getString("longitude"), resultado.getString("city"), resultado.getInt("type"), resultado.getDouble("rating"), resultado.getDouble("pricePerNight"), resultado.getString("thumbnail"), resultado.getString("agencyCode"))
        arrayHomes += aux
      }

      // Ya con nuestros resultados preparados, cerramos la conexion y retornamos los resultados
      conexion.close()
      Some(Json.toJson(arrayHomes))
    }
    catch {
      // En caso de error, cerramos la conexion a la BD y retornamos None
      case e: Exception => 
        conexion.close()
        None
    }
  }
  
  // Metodo para recuperar los inmuebles que concuerdan con los parametros de busqueda del usuario
  // Entrada: Json con los parametros de busqueda
  // Salida: Json con los datos de los inmuebles
  def searchFunction(request: Option[JsValue]) : Option[JsValue] = {
    request match {
      case None => // Primero que todo, si NO llego nada (o sea, None) en el cuerpo del mensaje entonces
        // Retornamos de inmediato y con un json decimos que no recibimos nada
        Some(Json.obj("status" -> "Error", "message" -> "Request vacio!!!"))
      case Some(cuerpoJson) => // En caso que si haya llegado un json con "algo" entonces
        val llaves = cuerpoJson.as[JsObject].keys // Sacamos la lista de claves (keys) de dicho json en un Set (Conjunto)
        // Y revisamos que si esten las 4 claves necesarias para realizar la busqueda
        // Por lo tanto, si NO estan todas las claves entonces
        if (!llaves.contains("checkIn") || !llaves.contains("checkOut") || !llaves.contains("city") || !llaves.contains("type"))
        {
          // Abortamos y retornamos un json donde decimos que faltan parametros
          Some(Json.obj("status" -> "Error", "message" -> "Request no tiene todos los parametros indicados"))
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
            Some(Json.obj("status" -> "Error", "message" -> "Las fechas deben ser tipo String"))
          }
          else // Si las fechas tienen el tipo correcto entonces
          {
            // Calculo/obtengo el numero de dias que se quiere hospedar el cliente 
            val numDays = countDays(arrivedDate.get, departureDate.get)
            
            numDays match {
              case None => // Luego, si se presento un error con el calculo de fechas (se debe obtener 'None') entonces
                // Se aborta y en un json se dice que las fechas no estan bien escritas
                Some(Json.obj("status" -> "Error", "message" -> "Las fechas no tienen el formato DD/MM/YYYY o DD-MM-YYYY"))
              case Some(numDays) if (numDays < 0) => // Por otro lado, si se obtiene que la diferencia es negativa entonces
                // Tambien aborto ya que eso significa que las fechas no tienen el orden correcto
                Some(Json.obj("status" -> "Error", "message" -> "Las fecha de partida no puede ser anterior a la fecha de llegada!"))
              case Some(numDays) if (numDays == 0) => // O si la diferencia es cero entonces
                // Igualmente aborto porque el hospedaje minimo es de un dia
                Some(Json.obj("status" -> "Error", "message" -> "La reserva debe ser de por lo menos de un dia!"))
              case Some(numDays) if (numDays > 0) => // Ahora, si el calculo de los dias fue correcto entonces
                // Creo una variable en donde se armara el fragmento de query que indicara que tipos de casas se van a buscar
                // Nota: Esta se inicializa como: '' (comillas vacias) ya que si homeType no tiene un contenido correcto entonces que no se busque nada
                var auxQuery = "`type`= ''"
              
                // Ahora, se intenta recuperar el valor de la clave 'type' como si fuera una lista
                val homeType = (cuerpoJson \ "type").asOpt[List[String]]
                
                homeType match {
                  case None => // Si 'type' NO es una lista entonces
                    // Trato de recuperar el valor de 'type' como si fuera un String
                    val homeType = (cuerpoJson \ "type").asOpt[String]
                    
                    homeType match {
                      case Some(hType) => // Y si DE VERDAD lo ES entonces
                        // Se arma el fragmento de query con este unico valor de busqueda
                        auxQuery = s"`type`= '${hType}'"
                      case None => // En cualquier otro caso, simplemente se deja tal como esta
                        auxQuery = "`type`= ''"
                    }
                  case Some(hType) =>  // En caso que 'type' SI es una lista entonces
                    // Revisamos que la lista no este vacia y SI NO LO ESTA entonces
                    if (!(hType.isEmpty))
                    {
                      // Se arma el fragmento de query de busqueda con cada uno de los tipos de casa definidos en la lista, por lo que...
                      auxQuery = s"`type`= '${hType(0)}'" // Se escribe el primer tipo de casa a buscar
                      for (cont <- 1 until hType.length) // Y para el resto se le concatena a lo ya escrito con 'OR'
                      {
                        auxQuery = auxQuery + s" OR `type`= '${hType(cont)}'"
                      }
                    }
                }
                
                // Despues, se intenta recuperar el codigo de ciudad
                val cityCode = (cuerpoJson \ "city").asOpt[String]
                
                cityCode match {
                  case None => // De modo que, si este codigo NO es una hilera entonces
                    // Se aborta y en un json se dice que el parametro 'city' tiene un tipo incorrecto
                    Some(Json.obj("status" -> "Error", "message" -> "El tipo del parametro 'city' debe ser String"))
                  case Some(cCode) => // Sino entonces
                    // Creo un Json vacio, en el cual se ira construyendo la respuesta final que se va a retornar
                    var jsonResponse = Json.obj()
                    
                    // Luego creamos una variable para realizar la conexion con la BD
                    val conexion = db.getConnection()
                
                    try
                    {
                      // Ahora creamos una variable para formular queries SQL
                      val query = conexion.createStatement
                      
                      // Como primer query, vamos a obtener los datos de la agencia y a formatear los mismos a un json
                      val resultado1 = query.executeQuery("SELECT nit, name, description FROM Agency")
                      resultado1.next()
                      val jsonInfoAgency = Json.obj("nit" -> resultado1.getString("nit"),
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
                        WHERE (`city`='${cCode}') AND (${auxQuery});""")
                      
                      // Despues, se crea un arreglo json vacio el cual se ira rellenando con jsons que tengan los datos de cada una de las casas
                      var arrayHomes = JsArray()
                      while (resultado2.next()){
                        val jsonAux = Json.obj("id" -> resultado2.getInt("id"),
                                               "name" -> resultado2.getString("name"),
                                               "description" -> resultado2.getString("description"),
                                               "location" -> Json.obj("address" -> resultado2.getString("address"), "latitude" -> resultado2.getString("latitude"), "longitude" -> resultado2.getString("longitude")),
                                               "city" -> resultado2.getString("city"),
                                               "type" -> resultado2.getString("type"),
                                               "rating" -> resultado2.getDouble("rating"),
                                               "totalAmount" -> (numDays)*resultado2.getDouble("pricePerNight"),
                                               "pricePerNight" -> resultado2.getDouble("pricePerNight"),
                                               "thumbnail" -> resultado2.getString("thumbnail"))
                        arrayHomes = arrayHomes :+ jsonAux
                      }
                      
                      // Al terminar de rellenar el arreglo de inmuebles, dicho arreglo se adjunta al json de respuesta bajo la clave 'homes'
                      jsonResponse = jsonResponse + ("homes" -> arrayHomes)
                      
                      // Antes de terminar (sea que la consulta a la BD sea exitosa o no), cerramos la conexion a la BD
                      conexion.close()
                      
                      // Y se retorna el Json de respuesta
                      Some(jsonResponse)
                    }
                    catch
                    {
                      // En caso de error, retornamos un mensaje al respecto
                      case _: Throwable =>
                        // Antes de terminar (sea que la consulta a la BD sea exitosa o no), cerramos la conexion a la BD
                        conexion.close()
                        Some(Json.obj("status" -> "Error", "message" -> "Hubo un error, mientras se consultaba la BD!"))
                    }
                  }
              }
          }
        }
    }
  }
  
  // Release No. 2 ----------------------------------------------------
  
  // Metodo para instanciar la conexion con Firebase
  // Entrada: Ninguna
  // Salida: Valor booleano que indica si se logro establecer conexion con Firebase
  def setFireBaseConnection :Boolean = {
    // En primer lugar, revisamos que instancias de Firebase hay vigentes
    val fbApps = FirebaseApp.getApps()
    
    // Si no hay ninguna instancia de conexion vigente entonces
    if (fbApps.isEmpty)
    {
      // Establecemos los parametros que necesitamos para conectarnos a FireBase
      val initialFile = new File("yotearriendo.json");
      val credentials: InputStream = new FileInputStream(initialFile);
      val options = new FirebaseOptions.Builder()
      .setServiceAccount(credentials)
      .setDatabaseUrl("""https://yotearriendo-d532f.firebaseio.com/""")
      .build();
      
      // E intentamos inicializar la conexión con Firebase
      FirebaseApp.initializeApp(options);
    }
    
    // Finalmente, retornamos verdadero si en verdad se logro la comunicacion con Firebase o falso en caso contrario
    if (!FirebaseApp.getApps().isEmpty) {true} else {false}
  }
  
  // Metodo para revisar que un token de Firebase es valido
  // Entrada: Token de Firebase
  // Salida: Si el token es valido entonces se retorna el correo asociado al mismo, sino se retorna None
  def verifyIdToken(idToken: String): Option[String] = {
    // En primer lugar, invocamos el metodo de conexion a Firebase
    setFireBaseConnection

    try
    {
      // Luego, tratamos de decodificar el token de modo que trato de recuperar el correo del mismo y lo retorno
      val decodedToken = Tasks.await(FirebaseAuth.getInstance().verifyIdToken(idToken))
      val email = decodedToken.getEmail();
      Some(email)
    }
    catch // En caso de error entonces retorno falso
    {
      case e:Exception=>
        None
    }
  }
  
  // Metodo para obtener el UID dado un token de Firebase
  // Entrada: Token de Firebase
  // Salida: Si el token es valido se retorna el UID del usuario, sino se retorna "ERROR!"
  def getUID(idToken: String): String = {
    try
    {
      // En primer lugar, invocamos el metodo de conexion a Firebase
      setFireBaseConnection
      
      // Luego, tratamos de decodificar el token y retornamos el UID del mismo
      val decodedToken = Tasks.await(FirebaseAuth.getInstance().verifyIdToken(idToken))
      decodedToken.getUid();
    }
    catch // En caso de error entonces retorno ERROR!
    {
      case e:Exception=>
        "ERROR!"
    }
  }
  
  // Metodo que realiza la logica (el trabajo) de las reservas
  // Entrada: request -> Json con los parametros de la reserva
  //          token -> Token para validar la operacion
  // Salida: Json con el resultado de la operacion
  def bookingFunction(request: Option[JsValue], token: Option[String]) : Option[JsValue] = {
    // Primero, recupero la informacion de la agencia
    val infoAgency = getAgencyInfoFunction()
    
    infoAgency match {
      case None => // Si no se pudo recuperar la info de la agencia entonces
        // Abortamos y retornamos un Json indicando del problema
        Some(Json.obj("status" -> "Error", "message" -> "Tenemos problemas para accdeder a la informacion de la agencia"))
      case Some(infoAgency) => // Sino hay problemas entonces
        // Reviso el cuerpo de la peticion, de modo que 
        request match {
        case None => // Si no se envio nada por el cuerpo de la peticion entonces
          // Se retorna un mensaje de que el json request estaba vacio
          Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Request vacio!!!"))
        case Some(cuerpoJson) => // En caso que si haya llegado un json con "algo" entonces
          val llaves = cuerpoJson.as[JsObject].keys // Sacamos la lista de claves (keys) de dicho json en un Set (Conjunto)
          
          // Y revisamos que si esten las 3 claves necesarias para realizar la reserva
          // Por lo tanto, si NO estan todas las claves entonces
          if (!llaves.contains("checkIn") || !llaves.contains("checkOut") || !llaves.contains("id"))
          {
            // Abortamos y retornamos un json que indica que faltan parametros
            Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Request no tiene todos los parametros indicados"))
          }
          else // En caso que si esten los parametros entonces
          {
            // Pasamos a revisar que si tengamos un token de autenticacion
            token match {
              case None => // Si el token es nulo entonces
                // Abortamos y retornamos un json indicando que no se envio ningun token por el encabezado
                Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "No hay ninguna clave token en el encabezado"))
              case Some(authToken) => // Sino entonces almacenamos su valor en authToken
                // E invocamos el metodo de verificacion del token (recuperando al mismo tiempo, el correo asociado a este)
                val emailToken = verifyIdToken(authToken)
                
                emailToken match {
                  case None => // Ahora, si el token enviado no es valido entonces
                    // Abortamos y retornamos un mensaje de que el token no es valido
                    Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Token de usuario invalido"))
                  case Some(emailBooking) => // En caso que el token si sea valido entonces
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
                      Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Las fechas deben ser tipo String"))
                    }
                    else // Si las fechas tienen el tipo correcto entonces
                    {
                      // Intento recuperar el id del inmueble que el usuario quiere reservar
                      val idHome = (cuerpoJson \ "id").asOpt[Int]
                      
                      idHome match {
                        case None => // Si tengo problemas en recuperar tal ID entonces
                          // Aborto y retorno un json indicando que el ID del inmueble debe ser un numero entero
                          Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "El ID del inmueble debe ser un numero"))
                        case Some(idH) => // Si el id del inmueble tiene el tipo correcto entonces
                          // Se crea una variable para realizar la conexion con la BD y se crea una variable para formular queries SQL
                          val conexion = db.getConnection()
                          val query = conexion.createStatement
                        
                          try
                          {
                            // Luego, busco el inmueble con el id especificado
                            val resultado1 = query.executeQuery(s"SELECT COUNT(*) as numHomes FROM Home WHERE id = ${idH};")
                            resultado1.next()
                            
                            // Si la busqueda no arrojo ningun resultado entonces
                            if (resultado1.getInt("numHomes") == 0)
                            {
                              // Aborto y retorno un json que indica que no hay ningun inmueble con el id especificado
                              Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "El ID del inmueble no existe en la BD"))
                            }
                            else // En caso que si exista el inmueble
                            {
                              // Calculo/obtengo el numero de dias de la reserva
                              val numDays = countDays(arrivedDate.get, departureDate.get)
                              
                              numDays match {
                                case None => // De modo que, si se presento un error con el calculo de fechas (se debe obtener 'None') entonces
                                  // Se aborta y en un json se dice que las fechas no estan bien escritas
                                  Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Las fechas no tienen el formato DD/MM/YYYY o DD-MM-YYYY"))
                                case Some(numDays) if (numDays < 0) => // Por otro lado, si se obtiene que la diferencia es negativa entonces
                                  // Tambien aborto ya que eso significa que las fechas no tienen el orden correcto
                                  Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Las fecha de partida no puede ser anterior a la fecha de llegada!"))
                                case Some(numDays) if (numDays == 0) => // O si la diferencia es cero entonces
                                  // Igualmente aborto porque el hospedaje minimo es de un dia
                                  Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "La reserva debe ser de por lo menos de un dia!"))
                                case Some(numDays) if (numDays > 0) => // Ahora, si el calculo de los dias fue correcto entonces
                                  // Recupero todas las reservas asociadas al inmueble deseado
                                  // Nota: No se si es por la zona horaria del servidor que aloja la BD o si es por alguna otra configuracion, pero si ejecuto
                                  //       la siguiente instruccion, los registros que recupero tienen por una extraña razon las fechas con un dia de adelanto...
                                  //       Simplemente MUY RARO... por eso ejecuto la otra instruccion en donde a cada fecha le RESTO un DIA
                                  //val resultado2 = query.executeQuery(s"SElECT * FROM Booking WHERE homeId = ${idH};")
                                  val resultado2 = query.executeQuery(s"SElECT homeId, DATE_ADD(checkIn, INTERVAL -1 DAY) AS checkIn, DATE_ADD(checkOut, INTERVAL -1 DAY) AS checkOut, idClient, bookingId FROM Booking WHERE homeId = ${idH};")
                                  
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
                                    Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> s"Reserva invalida por fechas solapadas! El inmueble esta ocupado del ${auxArrivedDate} al ${auxDepartureDate}"))
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
                                    //val resultadoReserva = query.executeUpdate(s"""INSERT INTO Booking VALUES (${idH}, '${checkIn}', '${checkOut}', "${emailBooking}", NULL);""")
                                    val resultadoReserva = query.executeUpdate(s"""INSERT INTO Booking VALUES (${idH}, '${checkIn}'+INTERVAL 1 DAY, '${checkOut}'+INTERVAL 1 DAY, "${emailBooking}", NULL);""")
                                    
                                    // ---------- Forma alternativa de reserva con el UID ----------
                                    // Recuperamos el UID del token (identificador unico del usuario) y ejecutamos la query de reserva
                                    /*val clientUID = getUID(authToken)
                                    //val resultadoReserva = query.executeUpdate(s"""INSERT INTO Booking VALUES (${idH}, '${checkIn}', '${checkOut}', "${clientUID}", NULL);""")
                                    val resultadoReserva = query.executeUpdate(s"""INSERT INTO Booking VALUES (${idH}, '${checkIn}'+INTERVAL 1 DAY, '${checkOut}'+INTERVAL 1 DAY, "${clientUID}", NULL);""")
                                    */
                                    // -------------------------------------------------------------
                                    
                                    // Antes de terminar, cierro la conexion con la BD
                                    conexion.close()
                                    
                                    // Y retorno el mensaje de exito en la operacion de reserva
                                    Some(Json.obj("agency" -> infoAgency, "codigo" -> successCode, "mensaje" -> "Reserva con exito!!!"))
                                  }
                                }
                            }
                          }
                          catch
                          {
                            // En caso de error, retornamos un mensaje al respecto
                            case _: Throwable =>
                              conexion.close() // Antes de terminar (sea que la consulta a la BD sea exitosa o no), cerramos la conexion a la BD
                              Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Hubo un error, mientras se consultaba la BD!"))
                          }
                      }
                    }
                }
            } 
          }
      }
    }
  }
  
  // Metodo para consultar las reservas de un usuario
  // Entrada: El token para validar la operacion (y que al mismo tiempo sirve para obtener el email [el cual es el id de la reserva])
  // Salida: Json con el resultado de la operacion
  def getBookingFunction(token: Option[String]) :Option[JsValue] = {
    // Primero, recupero la informacion de la agencia
    val infoAgency = getAgencyInfoFunction()
    
    infoAgency match {
      case None => // Si no se pudo recuperar la info de la agencia entonces
        // Abortamos y retornamos un Json indicando del problema
        Some(Json.obj("status" -> "Error", "message" -> "Tenemos problemas para accdeder a la informacion de la agencia"))
      case Some(infoAgency) => // Sino hay problemas entonces
        // Pasamos a revisar que si tengamos un token de autenticacion
        token match {
          case None => // Si el token es nulo entonces
            // Abortamos y retornamos un json indicando que no se envio ningun token por el encabezado
            Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "No hay ninguna clave token en el encabezado"))
          case Some(authToken) => // Sino entonces almacenamos su valor en authToken
            // E invocamos el metodo de verificacion del token (recuperando al mismo tiempo, el correo asociado a este)
            val emailToken = verifyIdToken(authToken)
            
            emailToken match {
                  case None => // Ahora, si el token enviado no es valido entonces
                    // Abortamos y retornamos un mensaje de que el token no es valido
                    Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Token de usuario invalido"))
                  case Some(emailUser) => // En caso que el token si sea valido entonces
                    // Se crea una variable para realizar la conexion con la BD
                    val conexion = db.getConnection()
                    
                    try
                    {
                      // Luego, se construye la query o instruccion de consulta para las reservas
                      val q = """SELECT h.id, h.name, h.description, h.address, h.latitude, h.longitude, c.name as 'city', th.nameType as 'type', h.rating, h.pricePerNight, h.thumbnail, b.checkIn, b.checkOut, b.bookingId
                                 FROM Home as h
                                 JOIN City as c ON c.code = h.city
                                 JOIN TypeHome as th ON th.idType = h.type
                                 JOIN Booking as b ON b.homeId = h.id
                                 WHERE b.idClient=?
                                 ORDER BY h.id;"""
                      
                      // Se crea tambien una variable en donde formularemos nuestra query SQL de busqueda
                      val query = conexion.prepareStatement(q)
                      
                      // Ingresamos el parametro del cliente (o correo) a la query de busqueda
                      query.setString(1, emailUser)
                      
                      // Y se realiza/ejecuta la busqueda
                      val result = query.executeQuery();
                      
                      // Antes de pasar a analizar los resultados de la consulta, creamos e inicializamos algunas variables de apoyo
                      var jsonHomes = JsArray()                             // En esta se iran acumulando las casas reservadas
                      var jsonBookings = JsArray()                          // En esta se iran acumulando las reservas asociadas a las casas
                      var totalTotal = 0.0                                  // Esta variable nos permitira: 1ero) Ir sumando el monto total de las reservas de cada casa, 2do) Saber cuando agregar la ultima casa y sus reservas, y 3ero) Determinar si se obtuvieron resultados en la consulta
                      var idHomeAnt = 0                                     // Esta variable nos permitira identificar cuando hay una cambio de casa (para que en la siguiente iteracion se agregue la casa anterior con sus reservas a la lista de casas reservadas)
                      var jsonAuxHome = Json.obj()                          // Esta variable me permitira conservar de manera temporal la informacion de la casa anterior
                      var auxArrivedDate = ""                               // Con esta variable tomare la fecha de inicio de la reserva
                      var auxDepartureDate = ""                             // Con esta variable tomare la fecha de fin de la reserva
                      val formateadorDMY = new SimpleDateFormat("d-M-y")    // Y con esta variable podre cambiar el formato de las fechas (De YMD a DMY)
                      
                      // Ahora, mientras existan resultados (reservas asociadas) al cliente se procede a:
                      while (result.next()){
                        // Si (el id de la casa que se esta analizando es diferente de la anterior) Y (el acumulado del monto es diferente de cero) entonces
                        // NOTA: El segundo condicional es el que me permite saber si apenas entro al ciclo, por lo que si el monto es cero es porque todavia no ha analizado ninguna reserva
                        if ((idHomeAnt != result.getInt("id")) && (totalTotal != 0.0))
                        {
                          // Se adjuntan las reservas acumuladas a la casa anterior y se guarda la casa con sus reservas a la lista de resultados
                          jsonHomes = jsonHomes :+ (jsonAuxHome + ("booking" -> jsonBookings))
                          
                          // Posteriormente, se vacia la lista de reservas y se reinicia el acumulado del monto para la siguiente casa a analizar
                          jsonBookings = JsArray()
                          totalTotal = 0.0
                        }
                        
                        // Ya sea que se haya encolado la casa anterior o no, procedemos a tomar las fechas de la reserva presente, las tranformamos al formato DIA-MES-ANO y obtenemos los dias de hospodaje de la reserva
                        auxArrivedDate = formateadorDMY.format(result.getDate("checkIn"))
                        auxDepartureDate = formateadorDMY.format(result.getDate("checkOut"))
                        val numDays = countDays(auxArrivedDate, auxDepartureDate)
                        
                        // Ahora, calculamos el costo/monto individual de la reserva, el cual es igual a...
                        val totalAmount :Double = numDays match {
                          case None => 0.0 // CERO si el metodo countDays FALLA, lo cual es muy poco probable ya que las fechas de la BD ya deben tener el formato correcto
                          case Some(nDays) => nDays*result.getDouble("pricePerNight") // El numero de dias x el precio de estancia del inmueble
                        }
                        
                        // Despues, sumamos el costo de esta reserva al monto total para el inmueble
                        totalTotal = totalTotal + totalAmount
                        
                        // Luego, creamos un Json con la informacion de la reserva
                        val jsonAuxBooking = Json.obj("checkIn" -> auxArrivedDate,
                                                      "checkOut" -> auxDepartureDate,
                                                      "totalAmount" -> totalAmount,
                                                      "bookingId" -> result.getString("bookingId"))
                        
                        // Y depositamos tal reserva en el arreglo de rersevas destinadas para el inmueble
                        jsonBookings = jsonBookings :+ jsonAuxBooking
                        
                        // Ahora, recuperamos la informacion del inmueble y lo depositamos de manera temporal en un Json
                        // El cual solo se adjuntara a la lista de inmuebles reservados por el cliente cuando se pase a una reserva con un inmueble asociado diferente
                        jsonAuxHome = Json.obj("id" -> result.getInt("id"),
                                                   "name" -> result.getString("name"),
                                                   "description" -> result.getString("description"),
                                                   "location" -> Json.obj("address" -> result.getString("address"), "latitude" -> result.getString("latitude"), "longitude" -> result.getString("longitude")),
                                                   "city" -> result.getString("city"),
                                                   "type" -> result.getString("type"),
                                                   "rating" -> result.getDouble("rating"),
                                                   "totalAmount" -> totalTotal,
                                                   "pricePerNight" -> result.getDouble("pricePerNight"),
                                                   "thumbnail" -> result.getString("thumbnail"))
                        
                        // Antes de pasar a la siguiente reserva, guardamos el id del inmueble vigente para compararlo con el siguiente
                        idHomeAnt = result.getInt("id")
                      }
                      
                      // Una vez que termina de analizar las reservas, SI el acumulado del monto es diferente de cero entonces
                      // Nota: Si en la consulta no se arrojaron resultados entonces no podra entrar al ciclo anterior y por tanto el acumulado sera igual a cero (lo que nos permite identificar cuando un usuario no tiene reservas)
                      if (totalTotal != 0.0)
                      {
                        // Se agrega la ultima casa con sus reservas a la lista de resultados
                        jsonHomes = jsonHomes :+ (jsonAuxHome + ("booking" -> jsonBookings))
                      }
                      
                      // Finalmente, se retorna la info de la agencia junto a los inmuebles que se tienen reservados con cada una de sus reservas asociadas
                      Some(Json.obj("agency" -> infoAgency, "homes" -> jsonHomes))
                    }
                    catch
                    {
                      // En caso de error, retornamos un mensaje al respecto
                      case _: Throwable =>
                        conexion.close() // Antes de terminar (sea que la consulta a la BD sea exitosa o no), cerramos la conexion a la BD
                        Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Hubo un error, mientras se consultaba la BD!"))
                    }
            }
        }
    }
  }
  
  // Metodo para eliminar la reserva indicada
  // Entrada: request -> Json con el id de la reserva a eliminar
  //          token -> Token para validar la operacion
  // Salida: Json con el resultado de la operacion
  def removeBookingFunction(request: Option[JsValue], token: Option[String]) : Option[JsValue] = {
    // Primero, recupero la informacion de la agencia
    val infoAgency = getAgencyInfoFunction()
    
    infoAgency match {
      case None => // Si no se pudo recuperar la info de la agencia entonces
        // Abortamos y retornamos un Json indicando del problema
        Some(Json.obj("status" -> "Error", "message" -> "Tenemos problemas para accdeder a la informacion de la agencia"))
      case Some(infoAgency) => // Sino hay problemas entonces
        // Reviso el cuerpo de la peticion, de modo que 
        request match {
        case None => // Si no se envio nada por el cuerpo de la peticion entonces
          // Se retorna un mensaje de que el json request estaba vacio
          Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Request vacio!!!"))
        case Some(cuerpoJson) => // En caso que si haya llegado un json con "algo" entonces
          val llaves = cuerpoJson.as[JsObject].keys // Sacamos la lista de claves (keys) de dicho json en un Set (Conjunto)
          
          // Y revisamos que si exista la clave "bookingId"
          // En caso que NO entonces
          if (!llaves.contains("bookingId"))
          {
            // Abortamos y retornamos un json que indica la ausencia de tal parametro
            Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Request no tiene la clave bookingId"))
          }
          else // En caso que si este la clave bookingId entonces
          {
            // Pasamos a revisar que si tengamos un token de autenticacion
            token match {
              case None => // Si el token es nulo entonces
                // Abortamos y retornamos un json indicando que no se envio ningun token por el encabezado
                Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "No hay ninguna clave token en el encabezado"))
              case Some(authToken) => // Sino entonces almacenamos su valor en authToken
                // Y pasamos a verificar que el token sea valido (nos debe entregar un email)
                val emailToken = verifyIdToken(authToken)
                
                emailToken match {
                  case None => // Ahora, si el token enviado no es valido entonces
                    // Abortamos y retornamos un mensaje de que el token no es valido
                    Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Token de usuario invalido"))
                  case Some(emailUser) => // En caso que el token si sea valido entonces
                    // Intento recuperar el id de la reserva
                    val bookingId = (cuerpoJson \ "bookingId").asOpt[String]
                    
                    bookingId match {
                      case None => // Si tengo problemas en recuperar tal ID entonces
                        // Aborto y retorno un json indicando que el ID de la reserva debe ser un String
                        Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "El ID de la reserva debe ser tipo texto"))
                      case Some(bId) => // Si el id de la reserva tiene el tipo correcto entonces
                        // Se crea una variable para realizar la conexion con la BD y se crea una variable para formular queries SQL
                        val conexion = db.getConnection()
                        val query = conexion.createStatement
                        
                        try
                        {
                          // Luego, busco la reserva con el id especificado
                          val resultado1 = query.executeQuery(s"SELECT COUNT(*) as numBookings FROM Booking WHERE bookingId = ${bId};")
                          resultado1.next()
                          
                          // Si la busqueda no arrojo ningun resultado entonces
                          if (resultado1.getInt("numBookings") == 0)
                          {
                            // Aborto y retorno un json que indica que no hay ningun inmueble con el id especificado
                            Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "No existe una reserva con el ID especificado en la BD"))
                          }
                          else // En caso que si exista el inmueble
                          {
                            // Entonces, ejecuto la instruccion para eliminar la reserva
                            val resultadoReserva = query.executeUpdate(s"""DELETE FROM Booking WHERE bookingId = ${bId};""")
                            
                            // Cierro la conexion con la BD
                            conexion.close()
                            
                            // Y retorno un mensaje de exito en la operacion de eliminacion
                            Some(Json.obj("agency" -> infoAgency, "codigo" -> successCode, "mensaje" -> "Cancelacion con exito!!!"))
                          }
                        }
                        catch
                        {
                          // En caso de error, retornamos un mensaje al respecto
                          case _: Throwable =>
                            conexion.close() // Antes de terminar (sea que la consulta a la BD sea exitosa o no), cerramos la conexion a la BD
                            Some(Json.obj("agency" -> infoAgency, "codigo" -> errorCode, "mensaje" -> "Hubo un error, mientras se consultaba la BD!"))
                        }
                    }
                }
            }
          }
        }
    }
  }
  
  // ##################################################################
  
  
  
  // ******************************************************************
  // **********************      SERVICIOS       **********************
  // ******************************************************************
  
  // Metodo que expone los diferentes servicios que ofrece la agencia
  // Entrada: Nombre del servicio a solicitar
  // Salida: Accion del servicio
  def serviceManager(service: String) = Action {implicit request =>
    try
    {
      // En primer lugar, invocamos el servicio que solicito el usuario
      // y en base al retorno del servicio inicializamos la variable result (o resultado)
      val result :Option[JsValue] = service match {
        case "infoAgency" =>
          getAgencyInfoFunction
        case "allHouses" =>
          getAllFunction
        case "searchHouses" =>
          searchFunction(request.body.asJson)
        case "booking" =>
          bookingFunction(request.body.asJson, request.headers.get("token"))
        case "getBookings" =>
          getBookingFunction(request.headers.get("token"))
        case "removeBooking" =>
          removeBookingFunction(request.body.asJson, request.headers.get("token"))
        case everythingElse =>
          None
      }
      
      // Si el resultado es None es porque se trato de acceder a un servicio que no existe
      if (result == None)
      {
        // Por tanto, retornamos un mensaje al respecto
        BadRequest(Json.obj("status" -> "Error", "message" -> "El servicio solicitado no existe!"))
      }
      else // Sino
      {
        // Simplemente retornamos el Json que debe entregar el servicio como resultado
        Ok(result.get)
      }
    }
    catch // En caso que se presente algun error, retornamos un mensaje al respecto
    {
      case _: Throwable =>
        Ok(Json.obj("status" -> "Error", "message" -> "El servicio solicitado tuvo un error!\nIntentenlo mas tarde!"))
    }
  }
  
  // ******************************************************************
  
  
  // ==================================================================
  // ======================   METODOS DE APOYO   ======================
  // ==================================================================
  
  // Metodo para calcular el numero de dias entre dos fechas
  // Entrada: date1 -> Fecha de inicio del rango
  //          date2 -> Fecha de fin del rango
  // Salida: Numero de dias entre la fecha 1 y 2
  def countDays(date1: String, date2: String): Option[Int] = {
    try
    {
      // En primer lugar tokenizamos la 1er fecha (ya sea que los delimitadores sean: '-' o '/')
      val aux1 = date1.split(Array('-', '/'))
      val day1 = aux1(0).toInt // El 1er token debe ser el dia
      val month1 = aux1(1).toInt // El 2do token debe ser el mes
      val year1 = aux1(2).toInt // El 3er token debe ser el ano
      
      // Ahora, creamos un objeto datatime con los datos que obtuvimos de la primera fecha
      val jodaDate1 = new DateTime(year1, month1, day1, 0, 0)
      
      // Igualmente tokenizamos la 2da fecha
      val aux2 = date2.split(Array('-', '/'))
      val day2 = aux2(0).toInt // El 1er token debe ser el dia
      val month2 = aux2(1).toInt // El 2do token debe ser el mes
      val year2 = aux2(2).toInt // El 3er token debe ser el ano
      
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
  // Entrada: startDateA -> Fecha de inicio del rango 1
  //          endDateA -> Fecha de fin del rango 1
  //          startDateB -> Fecha de inicio del rango 2
  //          endDateB -> Fecha de fin del rango 2
  // Salida: Valor booleano que indica si las fechas se intersectan
  // Link que me ayudo:
  //   Determine Whether Two Date Ranges Overlap
  //   https://stackoverflow.com/questions/325933/determine-whether-two-date-ranges-overlap?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa
  def dateRangesOverlap(startDateA: String, endDateA: String, startDateB: String, endDateB: String): Boolean = {
    // Para empezar, tokenizamos la 1er fecha del primer intervalo (ya sea que los delimitadores sean: '-' o '/')
    val aux1 = startDateA.split(Array('-', '/'))
    val day1A = aux1(0).toInt       // El 1er token debe ser el dia
    val month1A = aux1(1).toInt     // El 2do token debe ser el mes
    val year1A = aux1(2).toInt      // El 3er token debe ser el ano
    
    // Ya separado correctamente el dia, el mes y el ano, creamos un objeto datatime con los datos que obtuvimos
    val jodaDate1A = new DateTime(year1A, month1A, day1A, 0, 0)
    
    // Luego, tokenizamos la 2da fecha del primer intervalo
    val aux2 = endDateA.split(Array('-', '/'))
    val day2A = aux2(0).toInt       // El 1er token debe ser el dia
    val month2A = aux2(1).toInt     // El 2do token debe ser el mes
    val year2A = aux2(2).toInt      // El 3er token debe ser el ano
    
    // Nuevamente, creamos un objeto datatime con los datos que obtuvimos
    val jodaDate2A = new DateTime(year2A, month2A, day2A, 0, 0)
    
    // Ahora, tokenizamos la 1er fecha del segundo intervalo
    val aux3 = startDateB.split(Array('-', '/'))
    val day1B = aux3(0).toInt       // El 1er token debe ser el dia
    val month1B = aux3(1).toInt     // El 2do token debe ser el mes
    val year1B = aux3(2).toInt      // El 3er token debe ser el ano
    
    // Volvemos a crear un objeto datatime con los datos que obtuvimos
    val jodaDate1B = new DateTime(year1B, month1B, day1B, 0, 0)
    
    // Y, tokenizamos la 2da fecha del segundo intervalo
    val aux4 = endDateB.split(Array('-', '/'))
    val day2B = aux4(0).toInt       // El 1er token debe ser el dia
    val month2B = aux4(1).toInt     // El 2do token debe ser el mes
    val year2B = aux4(2).toInt      // El 3er token debe ser el ano
    
    // Y hacemos un ultimo objeto datatime con los datos que obtuvimos
    val jodaDate2B = new DateTime(year2B, month2B, day2B, 0, 0)
    
    // Finalmente, si (la fecha de inicio del 1er intervalo es menor o igual a la fecha de fin del 2do intervalo) y (la fecha de fin del 1er intervalo es mayor o igual a la fecha de inicio del 2do intervalo) entonces
    if ((jodaDate1A.isBefore(jodaDate2B) || jodaDate1A.isEqual(jodaDate2B)) && (jodaDate2A.isAfter(jodaDate1B) || jodaDate2A.isEqual(jodaDate1B)))
    {
      // Retornamos verdadero (o sea, que los intervalos se cruzan, es decir se solapan o al menos se tocan)
      true
    }
    else // sino
    {
      // Retornamos falso
      false
    }
  }
  
  // ==================================================================
  
  
  // Metodo para ingresar al index del backend (No prestarle atencion)
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}