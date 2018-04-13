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
    // Primero, se crea una lista vacia para manejar los datos de la agencia que llegan de la BD
    var infoAgency = List[Agency]()
    
    // Luego creamos una variable para realizar la conexion con la BD
    val conexion = db.getConnection()

    try {
      // Ahora creamos una variable en donde formularemos nuestra query SQL de busqueda y la ejecutamos
      val query = conexion.createStatement
      val resultado = query.executeQuery("SELECT * FROM Agency")

      // Si todo salio bien, entonces creamos un objeto agencia
      var agency = Agency(resultado.getString("nit"), resultado.getString("name"), resultado.getString("description"))

      // Y retornamos como un json los resultados (info de la agencia)
      Ok(Json.toJson(agency))
    }
    catch {
      // En caso de error, retornamos un mensaje al respecto
      case unknown => BadRequest(Json.obj("status" -> "Error", "message" -> "Hubo un error!"))
    }
    finally {
      // Antes de terminar sea que la consulta sea exitosa o no, cerramos la conexion a la BD
      conexion.close()
    }
  }
  
  /*
  // Metodo para recuperar todos los inmuebles de la agencia
  def getAll = Action {
    
  }
  
  // Metodo para recuperar los inmuebles indicados en el json del cuerpo
  def search = Action {
    
  }
  */

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}
