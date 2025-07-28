const { validate_basket_helper } = require("./basket_validation");

let input = {
    "basket": [
      {
        "product_code": "037000930396",
        "price": 1.29,
        "quantity": 32,
        "unit": "item"
      },
      {
        "product_code": "7106919588011",
        "price": 1.81,
        "quantity": 25,
        "unit": "item"
      },
      {
        "product_code": "037000925033",
        "price": 1.59,
        "quantity": 40,
        "unit": "item"
      },
      {
        "product_code": "8952803493171",
        "price": 4.67,
        "quantity": 23,
        "unit": "item"
      },
      {
        "product_code": "037000758365",
        "price": 1.99,
        "quantity": 26,
        "unit": "item"
      },
      {
        "product_code": "037000930419",
        "price": 8.07,
        "quantity": 25,
        "unit": "item"
      },
      {
        "product_code": "030772094990",
        "price": 2.22,
        "quantity": 6,
        "unit": "item"
      }
    ],
    "coupons": [
      {
        "gs1": "8112009988459000209133221498939137",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1734566400000,
          "campaign_end_time": 2051222399000,
          "redemption_start_time": 1734566400000,
          "redemption_end_time": 2051222399000,
          "primary_purchase_save_value": 3,
          "primary_purchase_requirements": 1,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 1,
          "second_purchase_requirements": 2,
          "second_purchase_req_code": 0,
          "third_purchase_requirements": 6,
          "third_purchase_req_code": 0,
          "save_value_code": 2,
          "applies_to_which_item": 2,
          "base_gs1": "811200998845900020",
          "description": "[POS SDK] Buy 1A and 2B and 3C, Get 3C free"
        },
        "base_gs1": "811200998845900020",
        "discount_in_cents": 477
      },
      {
        "gs1": "8112009988459000209133428735112618",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1734566400000,
          "campaign_end_time": 2051222399000,
          "redemption_start_time": 1734566400000,
          "redemption_end_time": 2051222399000,
          "primary_purchase_save_value": 3,
          "primary_purchase_requirements": 1,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 1,
          "second_purchase_requirements": 2,
          "second_purchase_req_code": 0,
          "third_purchase_requirements": 6,
          "third_purchase_req_code": 0,
          "save_value_code": 2,
          "applies_to_which_item": 2,
          "base_gs1": "811200998845900020",
          "description": "[POS SDK] Buy 1A and 2B and 3C, Get 3C free"
        },
        "base_gs1": "811200998845900020",
        "discount_in_cents": 477
      },
      {
        "gs1": "8112009988459000219133477015397373",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1734566400000,
          "campaign_end_time": 2051222399000,
          "redemption_start_time": 1734566400000,
          "redemption_end_time": 2051222399000,
          "primary_purchase_save_value": 2,
          "primary_purchase_requirements": 1,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 1,
          "second_purchase_requirements": 4,
          "second_purchase_req_code": 0,
          "third_purchase_requirements": 3,
          "third_purchase_req_code": 0,
          "save_value_code": 2,
          "applies_to_which_item": 1,
          "base_gs1": "811200998845900021",
          "description": "[POS SDK] Buy 1A and 2B and 3C, Get 2B free"
        },
        "base_gs1": "811200998845900021",
        "discount_in_cents": 362
      },
      {
        "gs1": "8112009988459000219133646330777360",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1734566400000,
          "campaign_end_time": 2051222399000,
          "redemption_start_time": 1734566400000,
          "redemption_end_time": 2051222399000,
          "primary_purchase_save_value": 2,
          "primary_purchase_requirements": 1,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 1,
          "second_purchase_requirements": 4,
          "second_purchase_req_code": 0,
          "third_purchase_requirements": 3,
          "third_purchase_req_code": 0,
          "save_value_code": 2,
          "applies_to_which_item": 1,
          "base_gs1": "811200998845900021",
          "description": "[POS SDK] Buy 1A and 2B and 3C, Get 2B free"
        },
        "base_gs1": "811200998845900021",
        "discount_in_cents": 362
      },
      {
        "gs1": "8112009988459000079133774642195686",
        "purchase_requirement": {
          "primary_purchase_gtins": [
            "037000920588",
            "037000761785",
            "037000974802",
            "037000831815",
            "037000930419",
            "037000930396"
          ],
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1731283200000,
          "campaign_end_time": 2048543999000,
          "redemption_start_time": 1731283200000,
          "redemption_end_time": 2048543999000,
          "primary_purchase_save_value": 2,
          "primary_purchase_requirements": 7,
          "primary_purchase_req_code": 0,
          "save_value_code": 2,
          "base_gs1": "811200998845900007",
          "description": "[POS SDK] Buy 5 Products in the group and get 2 Free"
        },
        "base_gs1": "811200998845900007",
        "discount_in_cents": 258
      },
      {
        "gs1": "8112009988459000079133646588189611",
        "purchase_requirement": {
          "primary_purchase_gtins": [
            "037000920588",
            "037000761785",
            "037000974802",
            "037000831815",
            "037000930419",
            "037000930396"
          ],
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1731283200000,
          "campaign_end_time": 2048543999000,
          "redemption_start_time": 1731283200000,
          "redemption_end_time": 2048543999000,
          "primary_purchase_save_value": 2,
          "primary_purchase_requirements": 7,
          "primary_purchase_req_code": 0,
          "save_value_code": 2,
          "base_gs1": "811200998845900007",
          "description": "[POS SDK] Buy 5 Products in the group and get 2 Free"
        },
        "base_gs1": "811200998845900007",
        "discount_in_cents": 258
      },
      {
        "gs1": "8112009988459000229133117549669850",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1734566400000,
          "campaign_end_time": 2051222399000,
          "redemption_start_time": 1734566400000,
          "redemption_end_time": 2051222399000,
          "primary_purchase_save_value": 1,
          "primary_purchase_requirements": 2,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 1,
          "second_purchase_requirements": 2,
          "second_purchase_req_code": 0,
          "third_purchase_requirements": 3,
          "third_purchase_req_code": 0,
          "save_value_code": 2,
          "applies_to_which_item": 0,
          "base_gs1": "811200998845900022",
          "description": "[POS SDK] Buy 1A and 2B and 3C, Get 1A free"
        },
        "base_gs1": "811200998845900022",
        "discount_in_cents": 129
      },
      {
        "gs1": "8112009988459000239133178439256353",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1735257600000,
          "campaign_end_time": 2051222399000,
          "redemption_start_time": 1735257600000,
          "redemption_end_time": 2051222399000,
          "primary_purchase_save_value": 0,
          "primary_purchase_requirements": 6,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 2,
          "second_purchase_requirements": 2,
          "second_purchase_req_code": 0,
          "third_purchase_requirements": 3,
          "third_purchase_req_code": 0,
          "save_value_code": 1,
          "applies_to_which_item": 0,
          "base_gs1": "811200998845900023",
          "description": "[POS SDK] Buy 5A, AND ( 2B Or 3C), Get 1A Free"
        },
        "base_gs1": "811200998845900023",
        "discount_in_cents": 129
      },
      {
        "gs1": "8112009988459000229133745171991510",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1734566400000,
          "campaign_end_time": 2051222399000,
          "redemption_start_time": 1734566400000,
          "redemption_end_time": 2051222399000,
          "primary_purchase_save_value": 1,
          "primary_purchase_requirements": 2,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 1,
          "second_purchase_requirements": 2,
          "second_purchase_req_code": 0,
          "third_purchase_requirements": 3,
          "third_purchase_req_code": 0,
          "save_value_code": 2,
          "applies_to_which_item": 0,
          "base_gs1": "811200998845900022",
          "description": "[POS SDK] Buy 1A and 2B and 3C, Get 1A free"
        },
        "base_gs1": "811200998845900022",
        "discount_in_cents": 129
      },
      {
        "gs1": "8112009988459000239133938400454254",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1735257600000,
          "campaign_end_time": 2051222399000,
          "redemption_start_time": 1735257600000,
          "redemption_end_time": 2051222399000,
          "primary_purchase_save_value": 0,
          "primary_purchase_requirements": 6,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 2,
          "second_purchase_requirements": 2,
          "second_purchase_req_code": 0,
          "third_purchase_requirements": 3,
          "third_purchase_req_code": 0,
          "save_value_code": 1,
          "applies_to_which_item": 0,
          "base_gs1": "811200998845900023",
          "description": "[POS SDK] Buy 5A, AND ( 2B Or 3C), Get 1A Free"
        },
        "base_gs1": "811200998845900023",
        "discount_in_cents": 129
      },
      {
        "gs1": "8112009988459000089133242775328587",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1731542400000,
          "campaign_end_time": 2048543999000,
          "redemption_start_time": 1731542400000,
          "redemption_end_time": 2048543999000,
          "primary_purchase_save_value": 2,
          "primary_purchase_requirements": 5,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 1,
          "second_purchase_requirements": 2,
          "second_purchase_req_code": 0,
          "save_value_code": 2,
          "applies_to_which_item": 1,
          "base_gs1": "811200998845900008",
          "description": "[POS SDK] Buy 5 Products in the A group and get 2 Free from Group B"
        },
        "base_gs1": "811200998845900008",
        "discount_in_cents": 0
      },
      {
        "gs1": "8112009988459000089133450735379963",
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
          "donot_multiply_flag": 1,
          "email_domain": "mobispark.thecouponbureau.org",
          "campaign_start_time": 1731542400000,
          "campaign_end_time": 2048543999000,
          "redemption_start_time": 1731542400000,
          "redemption_end_time": 2048543999000,
          "primary_purchase_save_value": 2,
          "primary_purchase_requirements": 5,
          "primary_purchase_req_code": 0,
          "additional_purchase_rules_code": 1,
          "second_purchase_requirements": 2,
          "second_purchase_req_code": 0,
          "save_value_code": 2,
          "applies_to_which_item": 1,
          "base_gs1": "811200998845900008",
          "description": "[POS SDK] Buy 5 Products in the A group and get 2 Free from Group B"
        },
        "base_gs1": "811200998845900008",
        "discount_in_cents": 0
      }
    ]
  }

  let output = validate_basket_helper(input);

  console.log(JSON.stringify(output, 2, null));