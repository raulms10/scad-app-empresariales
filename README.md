# scad-app-empresariales
Repositorio para realizar el proyecto de app empresariales

## Funcionaliades
> ### GET     /v1/agency/info
#### Descripción
Devuelve la información de la agencia (Arrendamientos Scad)
#### JSON entrada
N/A
#### JSON salida
```
{
    "nit":"1234-4567-00048-6553",
    "name":"Arrendamientos SCAD",
    "description":"Agencia de arrendamientos para estudiantes de la Universidad de Antioquia."
}
```

> ### GET     /v1/homes/all          

#### Descripción
Devuelve todas las casas registradas en el sistema
#### JSON entrada
N/A
#### JSON salida
```
[
    {
        "id": 1,
        "name": "Las Brisas 2",
        "description": "40m2, 2 habitaciones y un baño",
        "address": "Calle 56a #49-70",
        "latitude": "41.40338",
        "longitude": "2.17403",
        "city": "CO-MDE",
        "type": 1,
        "rating": 4.2,
        "pricePerNight": 100.25,
        "thumbnail": "https://es.seaicons.com/wp-content/uploads/2016/09/Actions-go-home-icon.png",
        "agencyCode": "1234-4567-00048-6553"
    },
    {
        "id": 2,
        "name": "Essenza",
        "description": "60m2, 5 habitaciones, 2 baños y solar",
        "address": "Circular 2 #74-43",
        "latitude": "16.56648",
        "longitude": "5.15665",
        "city": "CO-BGA",
        "type": 2,
        "rating": 3.75,
        "pricePerNight": 216.15,
        "thumbnail": "http://pngimages.net/sites/default/files/home-png-image-41830.png",
        "agencyCode": "1234-4567-00048-6553"
    }
]
```
> ### POST    /v1/homes/search     
#### Descripción
Devuelve las casas registradas según los parámetros de entrada
#### JSON entrada
```
{
    "checkIn": "07-04-2018",
    "checkOut": "10-04-2018",
    "city": "MED",
    "type": "1"
}
```
#### JSON salida
##### En caso de éxtio
```
{
    "agency": {
        "name": "Arrendamientos Santa Fé",
        "nit": "1123-1233-12313-51414"
    },
    "homes": [
        {
            "id": 1 ,
            "name": "Torre davivienda",
            "description": "Apartamento muy comodito",
            "location": {
                "address": "Avenida siempre viva 123",
                "latitude": "4.1231231",
                "longitude": "-421213212"
            },
            "city": "Medellín",
            "type": "Apartamento",
            "rating": "4.9",
            "totalAmount": "932.234",
            "pricePerNight": "100.233",
            "thumbnail": "https://goo.gl/sccYgg"
        }, 
        {
            "id": 2 ,
            "name": "La quinta porra",
            "description": "Apartamento muy lejos",
            "location": {
                "address": "Cra 1 # 1 A 1",
                "latitude": "5.12",
                "longitude": "21213212"
            },
            "city": "Medellín",
            "type": "Casa",
            "rating": "2",
            "totalAmount": "50.000",
            "pricePerNight": "20.000",
            "thumbnail": "https://goo.gl/sccYgg"
        }
    ]
}
```
##### En caso de error
```
{
    "message": "El recurso solicitado no se encuentra disponible."
}
```


