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
  
  /*
  // Metodo para recuperar los inmuebles indicados en el json del cuerpo
  def search = Action {
    
  }
  */

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}
