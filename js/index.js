const { validate_basket_helper } = require("./basket_validation");

  let input = {
  "basket": [
    {
      "product_code": "037000930396",
      "price": 1.29,
      "quantity": 6,
      "unit": "item"
    },
    {
      "product_code": "7106919588011",
      "price": 1.81,
      "quantity": 13,
      "unit": "item"
    },
    {
      "product_code": "030772036433",
      "price": 5.44,
      "quantity": 2,
      "unit": "item"
    },
    {
      "product_code": "037000925033",
      "price": 1.59,
      "quantity": 13,
      "unit": "item"
    },
    {
      "product_code": "037000930396",
      "price": 1.29,
      "quantity": 6,
      "unit": "item"
    },
    {
      "product_code": "8952803493171",
      "price": 4.67,
      "quantity": 9,
      "unit": "item"
    },
    {
      "product_code": "037000590804",
      "price": 5.11,
      "quantity": 4,
      "unit": "item"
    },
    {
      "product_code": "030772094969",
      "price": 4.76,
      "quantity": 4,
      "unit": "item"
    },
    {
      "product_code": "2066196461818",
      "price": 3.43,
      "quantity": 4,
      "unit": "item"
    },
    {
      "product_code": "037000758365",
      "price": 1.99,
      "quantity": 13,
      "unit": "item"
    },
    {
      "product_code": "030772075258",
      "price": 5.64,
      "quantity": 3,
      "unit": "item"
    },
    {
      "product_code": "037000930419",
      "price": 8.07,
      "quantity": 11,
      "unit": "item"
    },
    {
      "product_code": "6013644404626",
      "price": 5.35,
      "quantity": 2,
      "unit": "item"
    },
    {
      "product_code": "030772094990",
      "price": 2.22,
      "quantity": 2,
      "unit": "item"
    },
    {
      "product_code": "030772076880",
      "price": 5.52,
      "quantity": 7,
      "unit": "item"
    },
    {
      "product_code": "5901234123457",
      "price": 15.01,
      "quantity": 1,
      "unit": "item"
    },
    {
      "product_code": "037000530916",
      "price": 4.41,
      "quantity": 3,
      "unit": "item"
    },
    {
      "product_code": "037000653172",
      "price": 8.03,
      "quantity": 1,
      "unit": "item"
    },
    {
      "product_code": "037000916192",
      "price": 9.69,
      "quantity": 1,
      "unit": "item"
    }
  ],
  "coupons": [
    {
      "gs1": "8112009988459000129133768829435951",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "030772091630",
          "030772095003",
          "030772094990",
          "030772094969"
        ],
        "second_purchase_gtins": [
          "037000534365",
          "037000590798",
          "030772032237",
          "030772036433"
        ],
        "third_purchase_gtins": [
          "037000956914",
          "037000387046",
          "037000755142",
          "037000273325",
          "037000955764"
        ],
        "primary_purchase_save_value": 200,
        "primary_purchase_requirements": 500,
        "primary_purchase_req_code": 1,
        "additional_purchase_rules_code": 0,
        "second_purchase_requirements": 500,
        "second_purchase_req_code": 1,
        "third_purchase_requirements": 500,
        "third_purchase_req_code": 1,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900012"
    },
    {
      "gs1": "8112009988459000229133701244303370",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "037000618737"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "8952803493171",
          "1305192154937"
        ],
        "third_purchase_gtins": [
          "037000779681",
          "037000523505",
          "037000925033"
        ],
        "primary_purchase_save_value": 1,
        "primary_purchase_requirements": 2,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 2,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 3,
        "third_purchase_req_code": 0,
        "save_value_code": 2,
        "applies_to_which_item": 0
      },
      "base_gs1": "811200998845900022"
    },
    {
      "gs1": "8112009988459000259133185516163441",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "037000618737",
          "012345678912",
          "098765432112",
          "037000758365",
          "037000590804",
          "030772118061"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "8952803493171",
          "1305192154937",
          "4006381333931",
          "5012345678900"
        ],
        "primary_purchase_save_value": 199,
        "primary_purchase_requirements": 1,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 1,
        "second_purchase_req_code": 0,
        "save_value_code": 1,
        "applies_to_which_item": 0
      },
      "base_gs1": "811200998845900025"
    },
    {
      "gs1": "8112009988459000149133805040402560",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "030772095003",
          "030772094990",
          "030772094969"
        ],
        "second_purchase_eans": [
          "2066196461818",
          "4469208545601",
          "9332466362653",
          "7531785108429"
        ],
        "third_purchase_gtins": [
          "037000758365",
          "037000756507",
          "037000756231"
        ],
        "primary_purchase_save_value": 200,
        "primary_purchase_requirements": 500,
        "primary_purchase_req_code": 1,
        "additional_purchase_rules_code": 2,
        "second_purchase_requirements": 500,
        "second_purchase_req_code": 1,
        "third_purchase_requirements": 500,
        "third_purchase_req_code": 1,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900014"
    },
    {
      "gs1": "8112009988459000239133444997582221",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "037000618737",
          "037000758365"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "8952803493171",
          "1305192154937"
        ],
        "third_purchase_gtins": [
          "037000779681",
          "037000523505",
          "037000925033"
        ],
        "primary_purchase_save_value": 0,
        "primary_purchase_requirements": 6,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 2,
        "second_purchase_requirements": 2,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 3,
        "third_purchase_req_code": 0,
        "save_value_code": 1,
        "applies_to_which_item": 0
      },
      "base_gs1": "811200998845900023"
    },
    {
      "gs1": "8112009988459000219133223879896350",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "037000618737"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "8952803493171",
          "1305192154937"
        ],
        "third_purchase_gtins": [
          "037000779681",
          "037000523505",
          "037000925033"
        ],
        "primary_purchase_save_value": 2,
        "primary_purchase_requirements": 1,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 4,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 3,
        "third_purchase_req_code": 0,
        "save_value_code": 2,
        "applies_to_which_item": 1
      },
      "base_gs1": "811200998845900021"
    },
    {
      "gs1": "8112009988459000169133736410030271",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "030772075197",
          "030772075258",
          "037000358824"
        ],
        "second_purchase_eans": [
          "2248254457494",
          "0820544903168",
          "6013644404626"
        ],
        "primary_purchase_save_value": 200,
        "primary_purchase_requirements": 500,
        "primary_purchase_req_code": 1,
        "additional_purchase_rules_code": 0,
        "second_purchase_requirements": 500,
        "second_purchase_req_code": 1,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900016"
    },
    {
      "gs1": "8112009988459000209133587882077150",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "037000618737"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "8952803493171",
          "1305192154937"
        ],
        "third_purchase_gtins": [
          "037000779681",
          "037000523505",
          "037000925033"
        ],
        "primary_purchase_save_value": 3,
        "primary_purchase_requirements": 1,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 2,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 6,
        "third_purchase_req_code": 0,
        "save_value_code": 2,
        "applies_to_which_item": 2
      },
      "base_gs1": "811200998845900020"
    },
    {
      "gs1": "8112009988459000079133769222789901",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000920588",
          "037000761785",
          "037000974802",
          "037000831815",
          "037000930419",
          "037000930396"
        ],
        "primary_purchase_save_value": 2,
        "primary_purchase_requirements": 7,
        "primary_purchase_req_code": 0,
        "save_value_code": 2
      },
      "base_gs1": "811200998845900007"
    },
    {
      "gs1": "8112009988459000159133589790020941",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "030772075197",
          "030772075258",
          "037000358824"
        ],
        "second_purchase_eans": [
          "2248254457494",
          "0820544903168",
          "6013644404626"
        ],
        "primary_purchase_save_value": 100,
        "primary_purchase_requirements": 500,
        "primary_purchase_req_code": 1,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 300,
        "second_purchase_req_code": 1,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900015"
    },
    {
      "gs1": "8112009988459000049133103414813991",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000523550",
          "037000758365"
        ],
        "primary_purchase_eans": [
          "0483760951742",
          "3395218570238"
        ],
        "second_purchase_gtins": [
          "030772118054",
          "030772118092"
        ],
        "second_purchase_eans": [
          "3101233722039",
          "3679086800261"
        ],
        "third_purchase_gtins": [
          "037000534358",
          "037000808893"
        ],
        "third_purchase_eans": [
          "1719511543225",
          "5337669489231"
        ],
        "primary_purchase_save_value": 100,
        "primary_purchase_requirements": 2,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 0,
        "second_purchase_requirements": 2,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 2,
        "third_purchase_req_code": 0,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900004"
    },
    {
      "gs1": "8112009988459000089133226130651620",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "030772076835",
          "030772076880",
          "030772076934",
          "030772032244",
          "037000807667"
        ],
        "second_purchase_gtins": [
          "030772095003",
          "030772094990"
        ],
        "primary_purchase_save_value": 2,
        "primary_purchase_requirements": 5,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 2,
        "second_purchase_req_code": 0,
        "save_value_code": 2,
        "applies_to_which_item": 1
      },
      "base_gs1": "811200998845900008"
    },
    {
      "gs1": "8112009988459000099133820075347111",
      "purchase_requirement": {
        "primary_purchase_save_value": 100,
        "primary_purchase_requirements": 1000,
        "primary_purchase_req_code": 2,
        "save_value_code": 6
      },
      "base_gs1": "811200998845900009"
    },
    {
      "gs1": "8112009988459000189133211186037280",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "030772075197",
          "030772075258",
          "037000358824"
        ],
        "second_purchase_eans": [
          "2248254457494",
          "0820544903168",
          "6013644404626"
        ],
        "primary_purchase_save_value": 300,
        "primary_purchase_requirements": 500,
        "primary_purchase_req_code": 1,
        "additional_purchase_rules_code": 2,
        "second_purchase_requirements": 500,
        "second_purchase_req_code": 1,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900018"
    },
    {
      "gs1": "8112009988459000069133619787447350",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000920588",
          "037000761785",
          "037000974802",
          "037000831815",
          "037000930419"
        ],
        "primary_purchase_save_value": 0,
        "primary_purchase_requirements": 2,
        "primary_purchase_req_code": 0,
        "save_value_code": 1
      },
      "base_gs1": "811200998845900006"
    },
    {
      "gs1": "8112009988459000249133903816879100",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "037000618737"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "8952803493171",
          "1305192154937"
        ],
        "third_purchase_gtins": [
          "037000779681",
          "037000523505",
          "037000925033"
        ],
        "primary_purchase_save_value": 200,
        "primary_purchase_requirements": 5,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 2,
        "second_purchase_requirements": 2,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 3,
        "third_purchase_req_code": 0,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900024"
    },
    {
      "gs1": "8112009988459000139133267545843870",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "030772095003",
          "030772094990",
          "030772094969",
          "037000956914"
        ],
        "second_purchase_eans": [
          "2066196461818",
          "4469208545601",
          "9332466362653",
          "7531785108429"
        ],
        "third_purchase_gtins": [
          "037000758365",
          "037000756507",
          "037000756231"
        ],
        "primary_purchase_save_value": 300,
        "primary_purchase_requirements": 500,
        "primary_purchase_req_code": 1,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 500,
        "second_purchase_req_code": 1,
        "third_purchase_requirements": 500,
        "third_purchase_req_code": 1,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900013"
    },
    {
      "gs1": "8112009988459000199133504710017850",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "037000618737",
          "037000768104"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "8952803493171",
          "0006301862583",
          "1305192154937"
        ],
        "third_purchase_gtins": [
          "037000779681",
          "037000523505",
          "037000925033",
          "037000831600"
        ],
        "primary_purchase_save_value": 300,
        "primary_purchase_requirements": 1,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 2,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 3,
        "third_purchase_req_code": 0,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900019"
    },
    {
      "gs1": "8112009988459000059133140178143621",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000920588",
          "037000761785",
          "037000974802",
          "037000831815",
          "037000930419"
        ],
        "primary_purchase_save_value": 199,
        "primary_purchase_requirements": 2,
        "primary_purchase_req_code": 0,
        "save_value_code": 1
      },
      "base_gs1": "811200998845900005"
    },
    {
      "gs1": "8112009988459000179133911731890720",
      "purchase_requirement": {
        "second_purchase_gtins": [
          "037000530916",
          "037000750758",
          "037000542636",
          "098765432112"
        ],
        "primary_purchase_save_value": 100,
        "primary_purchase_requirements": 1000,
        "primary_purchase_req_code": 2,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 1,
        "second_purchase_req_code": 0,
        "save_value_code": 0,
        "applies_to_which_item": 1
      },
      "base_gs1": "811200998845900017"
    },
    {
      "gs1": "8112009988459000269133590254270211",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "012345678912"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "4006381333931",
          "5012345678900",
          "8952803493171"
        ],
        "third_purchase_gtins": [
          "098765432112",
          "037000758365",
          "037000590804"
        ],
        "primary_purchase_save_value": 199,
        "primary_purchase_requirements": 1,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 1,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 1,
        "third_purchase_req_code": 0,
        "save_value_code": 1,
        "applies_to_which_item": 2
      },
      "base_gs1": "811200998845900026"
    },
    {
      "gs1": "8112009988459000109133290291921431",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "030772036433",
          "030772036457",
          "037000591641"
        ],
        "primary_purchase_save_value": 250,
        "primary_purchase_requirements": 500,
        "primary_purchase_req_code": 1,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900010"
    },
    {
      "gs1": "8112009988459000279133269267434310",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "012345678912"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "4006381333931",
          "5012345678900",
          "8952803493171"
        ],
        "third_purchase_gtins": [
          "098765432112",
          "037000758365",
          "037000590804"
        ],
        "primary_purchase_save_value": 199,
        "primary_purchase_requirements": 1,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 1,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 1,
        "third_purchase_req_code": 0,
        "save_value_code": 1,
        "applies_to_which_item": 1
      },
      "base_gs1": "811200998845900027"
    },
    {
      "gs1": "8112009988459000119133912481407941",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000790068",
          "037000916192"
        ],
        "second_purchase_gtins": [
          "030772047439",
          "037000653172",
          "037000930389",
          "037000996637"
        ],
        "primary_purchase_save_value": 199,
        "primary_purchase_requirements": 1,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 1,
        "second_purchase_req_code": 0,
        "save_value_code": 1,
        "applies_to_which_item": 1
      },
      "base_gs1": "811200998845900011"
    },
    {
      "gs1": "8112009988459000039133624659452241",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000761648",
          "037000925323"
        ],
        "primary_purchase_eans": [
          "8374337327317",
          "6619059523398"
        ],
        "second_purchase_gtins": [
          "030772076835",
          "030772076880"
        ],
        "second_purchase_eans": [
          "8861424319336",
          "6418775526030"
        ],
        "primary_purchase_save_value": 100,
        "primary_purchase_requirements": 2,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 0,
        "second_purchase_requirements": 2,
        "second_purchase_req_code": 0,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900003"
    },
    {
      "gs1": "8112009988459000289133263265480791",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000758365",
          "037000934677",
          "012345678912",
          "030772118061"
        ],
        "second_purchase_eans": [
          "7106919588011",
          "4006381333931",
          "5012345678900",
          "8952803493171"
        ],
        "third_purchase_gtins": [
          "098765432112",
          "037000930396",
          "037000590804"
        ],
        "primary_purchase_save_value": 199,
        "primary_purchase_requirements": 1,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 1,
        "second_purchase_requirements": 1,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 1,
        "third_purchase_req_code": 0,
        "save_value_code": 1,
        "applies_to_which_item": 0
      },
      "base_gs1": "811200998845900028"
    },
    {
      "gs1": "8112009988459000019133773047131520",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "012345678912"
        ],
        "primary_purchase_eans": [
          "7106919588011",
          "8952803493171",
          "5012345678900"
        ],
        "primary_purchase_save_value": 100,
        "primary_purchase_requirements": 2,
        "primary_purchase_req_code": 0,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900001"
    }
  ]
}

  let output = validate_basket_helper(input);

  console.log(JSON.stringify(output, 2, null));
